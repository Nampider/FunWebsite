# Private chat backend

This project is a Spring Boot backend for a small, authenticated private chat. It provides session-based login, CSRF-protected HTTP endpoints, recent in-memory history, and a raw JSON WebSocket endpoint. The companion React test client is in the sibling `InfositeFrontend` application.

## Run locally

Java 21 or newer is required. Java 21 is the build target because it is the oldest LTS release with stable virtual threads and is easier to deploy than a short-lived JDK feature release. Set both required secrets before starting the app:

```powershell
$env:CHAT_CHRIS_PASSWORD = "use-a-long-unique-password"
$env:CHAT_AUDREY_PASSWORD = "use-another-long-unique-password"
$env:CHAT_FRONTEND_ORIGIN = "http://localhost:5173"
.\mvnw.cmd spring-boot:run
```

The application deliberately fails to start if either password is missing, blank, shorter than 8 characters, or longer than bcrypt's 72-byte input limit. Do not commit real passwords to this repository.

Run the verification suite with:

```powershell
.\mvnw.cmd test
```

## Deploy the backend to Render

The repository includes a multi-stage `Dockerfile` and a `render.yaml` Blueprint. Push this directory as its own GitHub repository, then create a Render Blueprint from that repository. The Blueprint creates one Docker web service on the free plan, enables automatic deploys, and configures `/actuator/health` as its health check.

Render prompts for these values during the Blueprint's first creation:

- `CHAT_CHRIS_PASSWORD`: a long, unique password
- `CHAT_AUDREY_PASSWORD`: a different long, unique password
- `CHAT_FRONTEND_ORIGIN`: the deployed frontend origin, such as `https://kinline-frontend.onrender.com`, with no path or trailing slash

The checked-in Blueprint supplies the non-secret production settings for secure cookies, forwarded proxy headers, and disabled debug endpoints. Render supplies `PORT`; do not add it manually. Real passwords belong only in Render's Environment page. The committed `.env.example` contains names and placeholders only.

Deploy the backend first, copy its `https://...onrender.com` URL into the frontend's `VITE_API_ORIGIN`, deploy the frontend, and then replace `CHAT_FRONTEND_ORIGIN` with the frontend's actual URL and redeploy this service.

Separate `onrender.com` services are cross-site, so the Blueprint uses `SESSION_COOKIE_SAME_SITE=none` with secure cookies. If both services later use HTTPS subdomains of the same custom domain, change this value to `lax`.

The free web service can spin down when idle. Because history and HTTP sessions are currently in memory, a spin-down, restart, or deploy clears history, requires users to sign in again, and closes active sockets. Use one paid always-running instance for dependable daily use; do not horizontally scale this in-memory version.

## Browser flow

All HTTP fetches from a separate frontend origin must use `credentials: "include"`.

1. `GET /api/auth/csrf`. Save the returned `headerName` and `token`; this request also establishes the HTTP session that stores the underlying CSRF token.
2. `POST /api/auth/login` as `application/x-www-form-urlencoded`, with fields `username` and `password`, and put the CSRF token in the returned header name.
3. After a successful login, call `GET /api/auth/csrf` again. Login changes the session ID and rotates the CSRF token.
4. `GET /api/auth/me` returns the authenticated user. `GET /api/messages?limit=50` returns recent messages.
5. Open `ws://localhost:8080/ws/chat` (use `wss://` in production). The browser sends the `JSESSIONID` cookie during the HTTP upgrade handshake.
6. Send frames shaped like `{"text":"Hello"}`. Server frames have `id`, `type`, `username`, `text`, and `sentAt` fields. `type` is `CHAT`, `SYSTEM`, or `ERROR`.
7. `POST /api/auth/logout` with the latest CSRF token. Logout invalidates the HTTP session and closes WebSockets created by that session.

Login errors are JSON `401` responses; missing/invalid CSRF tokens are JSON `403` responses. The public readiness endpoint is `GET /actuator/health`.

## How the WebSocket path works

The WebSocket starts as an ordinary HTTP `GET` upgrade request. CORS-style allowed-origin checking rejects browser handshakes not coming from `CHAT_FRONTEND_ORIGIN`, and the Spring Security filter chain requires the session to have the `CHATTER` role. Spring then copies the authenticated HTTP `Principal` and HTTP session ID into the WebSocket session.

After the `101 Switching Protocols` response, frames no longer pass through the servlet security filter chain. `ChatWebSocketHandler` therefore trusts only the handshake `Principal`, never a username supplied in a frame. It validates JSON, trims and bounds message text, limits frames to 4 KiB, and limits each connection to five inbound messages per second.

Active connections live in a concurrent map. Standard WebSocket sessions cannot safely have multiple threads call `sendMessage` at once, so every connection is wrapped in `ConcurrentWebSocketSessionDecorator`. A sent chat message is saved under a lock in the bounded history store and broadcast to each active connection. Slow or failed connections are bounded by a 10-second send limit and 64 KiB send buffer, then removed. Logout or HTTP-session expiry closes associated sockets.

## How virtual threads are used

`spring.threads.virtual.enabled=true` tells Spring Boot to use Java virtual threads for its application task executor and, with embedded Tomcat, for request/WebSocket container work. A virtual thread is a lightweight Java `Thread`: while it waits for socket or database I/O, the JVM can unmount it from its much smaller pool of OS carrier threads and run other work.

The chat handler intentionally uses straightforward synchronous code on the container's virtual thread. Creating an additional unbounded virtual thread for every frame would add scheduling and ordering complexity without helping this small amount of work. The in-memory store uses `ReentrantLock`, which cooperates with virtual threads. Virtual threads improve concurrency for blocking work; they do not make CPU-heavy work faster and they do not remove the need for payload, rate, connection, or database limits. `spring.main.keep-alive=true` is present because virtual threads are daemon threads and otherwise do not keep the JVM alive by themselves.

The debug endpoint `/api/debug/thread` is disabled by default. Set `CHAT_DEBUG_ENDPOINTS_ENABLED=true` only while diagnosing an authenticated development instance.

## Production checklist and current limits

For a single-instance, ephemeral two-person chat, this backend is complete once a compatible frontend and deployment environment are supplied. For a durable or horizontally scaled service, more infrastructure is required:

- The current `memory` history is erased on restart and is different in every server instance. Add a database-backed `ChatMessageStore` for durability.
- HTTP sessions and the live connection map are local to one process. Multiple instances need sticky sessions or a shared session store, plus a broker (for example Redis) to broadcast messages across instances.
- Terminate TLS at the application or a trusted reverse proxy. In production set `SESSION_COOKIE_SECURE=true`; use `FORWARD_HEADERS_STRATEGY=framework` only when the trusted proxy strips and rewrites forwarded headers.
- Prefer serving the frontend and backend from the same site. If they are truly cross-site, use `SESSION_COOKIE_SAME_SITE=none` together with HTTPS and the exact frontend origin. Never use `*` with credentialed CORS.
- Supply secrets through the hosting platform's secret manager, restrict actuator exposure, and retain only `health`/`info` unless another endpoint is intentionally secured.
- There is no account creation, password reset, audit trail, attachment support, end-to-end encryption, or frontend in this repository.

The leanest production topology is one HTTPS origin where a reverse proxy serves the frontend and forwards `/api` and `/ws` to this application. That avoids most cross-origin cookie complexity.
