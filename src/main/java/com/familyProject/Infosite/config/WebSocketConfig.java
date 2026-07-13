package com.familyProject.Infosite.config;

import com.familyProject.Infosite.chat.ChatWebSocketHandler;
import com.familyProject.Infosite.dto.ChatProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Collections;

@Configuration
@EnableWebSocket
public class WebSocketConfig
        implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatProperties chatProperties;

    public WebSocketConfig(
            ChatWebSocketHandler chatWebSocketHandler,
            ChatProperties chatProperties) {

        this.chatWebSocketHandler =
                chatWebSocketHandler;

        this.chatProperties =
                chatProperties;
    }

    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry) {

        String[] allowedOrigins =
                chatProperties.allowedOrigins()
                        .toArray(String[]::new);

        registry
                .addHandler(
                        chatWebSocketHandler,
                        "/ws/chat"
                )
                .addInterceptors(
                        new HttpSessionHandshakeInterceptor(
                                Collections.emptyList()
                        )
                )
                .setAllowedOrigins(
                        allowedOrigins
                );
    }
}
