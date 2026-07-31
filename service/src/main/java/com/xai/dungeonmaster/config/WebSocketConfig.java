package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.auth.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures STOMP-over-SockJS WebSocket support.
 *
 * Clients connect to:
 *   ws://localhost:8080/ws          (native WebSocket)
 *   http://localhost:8080/ws        (SockJS fallback)
 *
 * They then subscribe to:
 *   /topic/narrative                (default / unauthenticated)
 *   /topic/narrative/{sessionId}    (authenticated multi-player isolation)
 *
 * And they can send actions to:
 *   /app/action                     (handled by GameWebSocketController)
 *   /app/narrate                    (streaming narration)
 *
 * Pass {@code Authorization: Bearer <jwt>} as a STOMP CONNECT header to bind
 * the connection to a player session (see {@link StompAuthChannelInterceptor}).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuth;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuth) {
        this.stompAuth = stompAuth;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")   // tighten in production
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuth);
    }
}
