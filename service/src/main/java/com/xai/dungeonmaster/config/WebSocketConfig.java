package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.auth.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * Configures STOMP WebSocket support.
 *
 * Clients connect to:
 *   ws://localhost:8080/ws-stomp     (native WebSocket — mobile / OkHttp)
 *   http://localhost:8080/ws        (SockJS fallback for browsers)
 *
 * They then subscribe to:
 *   /topic/narrative                 (default / unauthenticated stream — auth off only)
 *   /topic/narrative/{sessionId}     (authenticated multi-player isolation)
 *
 * And they can send actions to:
 *   /app/action                      (handled by GameWebSocketController)
 *   /app/narrate                     (streaming narration)
 *
 * Pass {@code Authorization: Bearer <jwt>} as a STOMP CONNECT header to bind
 * the connection to a player session (see {@link StompAuthChannelInterceptor}).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuth;
    private final String[] allowedOriginPatterns;
    private final int messageSizeLimit;
    private final int sendBufferSizeLimit;

    public WebSocketConfig(
            StompAuthChannelInterceptor stompAuth,
            @Value("${game.cors.allowed-origins:*}") String allowedOrigins,
            @Value("${game.ws.message-size-limit:262144}") int messageSizeLimit,
            @Value("${game.ws.send-buffer-size-limit:524288}") int sendBufferSizeLimit) {
        this.stompAuth = stompAuth;
        this.allowedOriginPatterns = CorsConfig.originPatterns(allowedOrigins);
        this.messageSizeLimit = Math.max(16 * 1024, messageSizeLimit);
        this.sendBufferSizeLimit = Math.max(32 * 1024, sendBufferSizeLimit);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns)
                .withSockJS();

        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuth);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(messageSizeLimit);
        registration.setSendBufferSizeLimit(sendBufferSizeLimit);
        registration.setSendTimeLimit(15_000);
    }
}
