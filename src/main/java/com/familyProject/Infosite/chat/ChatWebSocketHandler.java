package com.familyProject.Infosite.chat;

import com.familyProject.Infosite.dto.ChatProperties;
import com.familyProject.Infosite.dto.IncomingChatMessage;
import com.familyProject.Infosite.dto.OutgoingChatMessage;
import com.familyProject.Infosite.enums.ChatMessageType;
import com.familyProject.Infosite.interfaces.ChatMessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
    private static final int SEND_BUFFER_SIZE_BYTES = 64 * 1024;

    private final JsonMapper jsonMapper;
    private final ChatMessageStore chatMessageStore;
    private final int maxMessageLength;
    private final int maxFrameSizeBytes;
    private final int maxMessagesPerSecond;

    /*
     * Standard WebSocket sessions do not support concurrent sends. Each entry
     * therefore wraps the container session in Spring's synchronized decorator.
     */
    private final ConcurrentMap<String, ClientSession> sessions =
            new ConcurrentHashMap<>();

    public ChatWebSocketHandler(
            JsonMapper jsonMapper,
            ChatMessageStore chatMessageStore,
            ChatProperties chatProperties) {

        this.jsonMapper = jsonMapper;
        this.chatMessageStore = chatMessageStore;
        this.maxMessageLength = chatProperties.maxMessageLength();
        this.maxFrameSizeBytes = chatProperties.maxFrameSizeBytes();
        this.maxMessagesPerSecond =
                chatProperties.maxMessagesPerSecond();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession originalSession) {
        Principal principal = originalSession.getPrincipal();

        if (principal == null) {
            closeQuietly(originalSession, CloseStatus.POLICY_VIOLATION);
            return;
        }

        originalSession.setTextMessageSizeLimit(maxFrameSizeBytes);

        Object httpSessionId = originalSession.getAttributes().get(
                HttpSessionHandshakeInterceptor.HTTP_SESSION_ID_ATTR_NAME
        );

        if (!(httpSessionId instanceof String sessionId)) {
            closeQuietly(originalSession, CloseStatus.POLICY_VIOLATION);
            return;
        }

        WebSocketSession safeSession =
                new ConcurrentWebSocketSessionDecorator(
                        originalSession,
                        SEND_TIME_LIMIT_MILLIS,
                        SEND_BUFFER_SIZE_BYTES
                );

        sessions.put(
                originalSession.getId(),
                new ClientSession(
                        safeSession,
                        new FixedWindowRateLimiter(maxMessagesPerSecond),
                        sessionId
                )
        );

        broadcast(systemMessage(
                principal.getName() + " joined the chat."
        ));
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession originalSession,
            TextMessage textMessage) {

        Principal principal = originalSession.getPrincipal();
        ClientSession client = sessions.get(originalSession.getId());

        if (principal == null || client == null) {
            closeQuietly(originalSession, CloseStatus.POLICY_VIOLATION);
            return;
        }

        if (!client.rateLimiter().tryAcquire()) {
            sendToSession(
                    client.socket(),
                    errorMessage("You are sending messages too quickly.")
            );
            return;
        }

        if (textMessage.asBytes().length > maxFrameSizeBytes) {
            closeQuietly(originalSession, CloseStatus.TOO_BIG_TO_PROCESS);
            return;
        }

        IncomingChatMessage incoming;

        try {
            incoming = jsonMapper.readValue(
                    textMessage.getPayload(),
                    IncomingChatMessage.class
            );
        } catch (JacksonException exception) {
            sendToSession(
                    client.socket(),
                    errorMessage("The message body was not valid JSON.")
            );
            return;
        }

        String cleanedText = incoming.text() == null
                ? ""
                : incoming.text().strip();

        if (cleanedText.isBlank()) {
            sendToSession(
                    client.socket(),
                    errorMessage("A message cannot be empty.")
            );
            return;
        }

        int characterCount = cleanedText.codePointCount(
                0,
                cleanedText.length()
        );

        if (characterCount > maxMessageLength) {
            sendToSession(
                    client.socket(),
                    errorMessage(
                            "Messages cannot exceed "
                                    + maxMessageLength
                                    + " characters."
                    )
            );
            return;
        }

        try {
            /* The authenticated Principal, never a client username, is trusted. */
            OutgoingChatMessage savedMessage = chatMessageStore.save(
                    principal.getName(),
                    cleanedText
            );

            broadcast(savedMessage);
        } catch (RuntimeException exception) {
            log.error("Could not process chat message", exception);
            sendToSession(
                    client.socket(),
                    errorMessage("The message could not be sent.")
            );
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession originalSession,
            CloseStatus status) {

        ClientSession removed = sessions.remove(originalSession.getId());
        Principal principal = originalSession.getPrincipal();

        if (removed != null && principal != null) {
            broadcast(systemMessage(
                    principal.getName() + " left the chat."
            ));
        }
    }

    @Override
    public void handleTransportError(
            WebSocketSession originalSession,
            Throwable exception) {

        sessions.remove(originalSession.getId());
        log.warn(
                "WebSocket transport error for session {}",
                originalSession.getId(),
                exception
        );
        closeQuietly(originalSession, CloseStatus.SERVER_ERROR);
    }

    @EventListener
    public void closeSocketsForDestroyedSession(
            SessionDestroyedEvent event) {

        sessions.forEach((webSocketId, client) -> {
            if (event.getId().equals(client.httpSessionId())
                    && sessions.remove(webSocketId, client)) {
                closeQuietly(
                        client.socket(),
                        CloseStatus.NORMAL.withReason(
                                "HTTP session ended"
                        )
                );
            }
        });
    }

    private OutgoingChatMessage systemMessage(String text) {
        return new OutgoingChatMessage(
                UUID.randomUUID().toString(),
                ChatMessageType.SYSTEM,
                null,
                text,
                Instant.now()
        );
    }

    private OutgoingChatMessage errorMessage(String text) {
        return new OutgoingChatMessage(
                UUID.randomUUID().toString(),
                ChatMessageType.ERROR,
                null,
                text,
                Instant.now()
        );
    }

    private void broadcast(OutgoingChatMessage message) {
        String json;

        try {
            json = jsonMapper.writeValueAsString(message);
        } catch (JacksonException exception) {
            log.error("Could not serialize chat message", exception);
            return;
        }

        sessions.forEach((sessionId, client) -> {
            WebSocketSession session = client.socket();

            if (!session.isOpen()) {
                sessions.remove(sessionId, client);
                return;
            }

            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException exception) {
                sessions.remove(sessionId, client);
                log.warn(
                        "Could not send message to session {}",
                        sessionId,
                        exception
                );
                closeQuietly(session, CloseStatus.SERVER_ERROR);
            }
        });
    }

    private void sendToSession(
            WebSocketSession session,
            OutgoingChatMessage message) {

        if (!session.isOpen()) {
            return;
        }

        try {
            session.sendMessage(new TextMessage(
                    jsonMapper.writeValueAsString(message)
            ));
        } catch (IOException exception) {
            log.warn(
                    "Could not send message to session {}",
                    session.getId(),
                    exception
            );
        }
    }

    private void closeQuietly(
            WebSocketSession session,
            CloseStatus status) {

        if (!session.isOpen()) {
            return;
        }

        try {
            session.close(status);
        } catch (IOException exception) {
            log.debug(
                    "Could not close WebSocket session {}",
                    session.getId(),
                    exception
            );
        }
    }

    private record ClientSession(
            WebSocketSession socket,
            FixedWindowRateLimiter rateLimiter,
            String httpSessionId) {
    }

    private static final class FixedWindowRateLimiter {

        private static final long WINDOW_NANOS =
                TimeUnit.SECONDS.toNanos(1);

        private final int limit;
        private long windowStartedAt = System.nanoTime();
        private int count;

        private FixedWindowRateLimiter(int limit) {
            this.limit = limit;
        }

        private synchronized boolean tryAcquire() {
            long now = System.nanoTime();

            if (now - windowStartedAt >= WINDOW_NANOS) {
                windowStartedAt = now;
                count = 0;
            }

            if (count >= limit) {
                return false;
            }

            count++;
            return true;
        }
    }
}
