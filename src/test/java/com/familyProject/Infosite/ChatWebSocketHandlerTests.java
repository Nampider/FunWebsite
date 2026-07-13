package com.familyProject.Infosite;

import com.familyProject.Infosite.chat.ChatWebSocketHandler;
import com.familyProject.Infosite.chat.InMemoryChatMessageStore;
import com.familyProject.Infosite.dto.ChatProperties;
import com.familyProject.Infosite.interfaces.ChatMessageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;
import tools.jackson.databind.json.JsonMapper;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "chat.users[0].username=chris",
        "chat.users[0].password=test-password-chris",
        "chat.users[1].username=audrey",
        "chat.users[1].password=test-password-audrey"
})
class ChatWebSocketHandlerTests {

    @Autowired
    private JsonMapper jsonMapper;

    private ChatMessageStore store;
    private ChatWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        ChatProperties properties = new ChatProperties(
                "memory",
                List.of("http://localhost:5173"),
                500,
                100,
                4096,
                5,
                List.of(new ChatProperties.AllowedUser(
                        "chris",
                        "test-password-chris"
                ))
        );

        store = new InMemoryChatMessageStore(properties);
        handler = new ChatWebSocketHandler(
                jsonMapper,
                store,
                properties
        );
    }

    @Test
    void authenticatedMessageUsesPrincipalAndIsStored() throws Exception {
        WebSocketSession session = authenticatedSession(
                "socket-1",
                "http-1",
                "chris"
        );

        handler.afterConnectionEstablished(session);
        handler.handleMessage(
                session,
                new TextMessage("{\"text\":\" hello \"}")
        );

        assertEquals(1, store.recent(100).size());
        assertEquals("chris", store.recent(1).getFirst().username());
        assertEquals("hello", store.recent(1).getFirst().text());
        verify(session).setTextMessageSizeLimit(4096);
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    void messageRateIsBoundedPerSocket() throws Exception {
        WebSocketSession session = authenticatedSession(
                "socket-2",
                "http-2",
                "chris"
        );
        handler.afterConnectionEstablished(session);

        for (int i = 0; i < 6; i++) {
            handler.handleMessage(
                    session,
                    new TextMessage("{\"text\":\"message " + i + "\"}")
            );
        }

        assertEquals(5, store.recent(100).size());
    }

    @Test
    void destroyedHttpSessionClosesItsWebSocket() throws Exception {
        WebSocketSession session = authenticatedSession(
                "socket-3",
                "http-3",
                "chris"
        );
        handler.afterConnectionEstablished(session);

        SessionDestroyedEvent event = mock(SessionDestroyedEvent.class);
        when(event.getId()).thenReturn("http-3");
        handler.closeSocketsForDestroyedSession(event);

        verify(session).close(CloseStatus.NORMAL.withReason(
                "HTTP session ended"
        ));
    }

    @Test
    void unauthenticatedSocketIsRejected() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }

    private static WebSocketSession authenticatedSession(
            String socketId,
            String httpSessionId,
            String username) {

        WebSocketSession session = mock(WebSocketSession.class);
        Principal principal = () -> username;

        when(session.getId()).thenReturn(socketId);
        when(session.getPrincipal()).thenReturn(principal);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(Map.of(
                HttpSessionHandshakeInterceptor
                        .HTTP_SESSION_ID_ATTR_NAME,
                httpSessionId
        ));

        return session;
    }
}
