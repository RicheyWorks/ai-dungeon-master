package com.xai.dungeonmaster.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures STOMP-over-SockJS WebSocket support.
 *
 * Replaces the raw ServerSocket / PrintWriter broadcast that lived inside
 * DungeonMasterEngine.startServer(port).
 *
 * Clients connect to:
 *   ws://localhost:8080/ws          (native WebSocket)
 *   http://localhost:8080/ws        (SockJS fallback)
 *
 * They then subscribe to:
 *   /topic/narrative                (engine broadcast messages)
 *
 * And they can send actions to:
 *   /app/action                     (handled by GameWebSocketController)
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker for /topic/** destinations
        registry.enableSimpleBroker("/topic");

        // Prefix for messages routed to @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")   // tighten in production
                .withSockJS();
    }
}
