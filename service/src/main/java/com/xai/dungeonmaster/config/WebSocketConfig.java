package com.xai.dungeonmaster.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures STOMP WebSocket support.
 *
 * Clients connect to:
 *   ws://localhost:8080/ws-stomp     (native WebSocket — mobile / OkHttp)
 *   http://localhost:8080/ws         (SockJS fallback for browsers)
 *
 * They then subscribe to:
 *   /topic/narrative                 (default / unauthenticated stream)
 *   /topic/narrative/{sessionId}     (when multi-player isolation is enabled)
 *
 * And they can send actions to:
 *   /app/action                      (handled by GameWebSocketController)
 *   /app/narrate                     (streaming narration)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS for browsers that need the fallback transports.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")   // tighten in production
                .withSockJS();

        // Native WebSocket for Android (OkHttp) and other non-SockJS clients.
        // Same STOMP app / broker prefixes; only the transport differs.
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*");
    }
}
