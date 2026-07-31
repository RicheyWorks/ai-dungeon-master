package com.xai.dungeonmaster.auth;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Binds an authenticated player session to a STOMP connection on CONNECT.
 *
 * Clients pass {@code Authorization: Bearer <jwt>} (or {@code X-Auth-Token})
 * as a STOMP native header when connecting to {@code /ws}. On success the
 * session id is stored under {@link #SESSION_ID_ATTR} in the WebSocket session
 * attributes so {@code @MessageMapping} handlers can resolve the caller's
 * isolated game engine.
 *
 * Unauthenticated connections are allowed (legacy single-player); they use
 * the process-default engine and {@code /topic/narrative}.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** WebSocket session attribute holding the player session id (String). */
    public static final String SESSION_ID_ATTR = "dm.sessionId";

    private final JwtService jwt;
    private final SessionService sessions;

    public StompAuthChannelInterceptor(JwtService jwt, SessionService sessions) {
        this.jwt = jwt;
        this.sessions = sessions;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token != null) {
                Optional<Map<String, Object>> claims = jwt.verify(token);
                if (claims.isPresent()) {
                    Object sub = claims.get().get("sub");
                    if (sub != null) {
                        Optional<SessionService.Session> session = sessions.touch(sub.toString());
                        if (session.isPresent() && accessor.getSessionAttributes() != null) {
                            accessor.getSessionAttributes().put(SESSION_ID_ATTR, session.get().id());
                        }
                    }
                }
            }
        }
        return message;
    }

    /** Read session id from STOMP session attributes (may be null). */
    public static String sessionIdOf(StompHeaderAccessor accessor) {
        if (accessor == null || accessor.getSessionAttributes() == null) {
            return null;
        }
        Object v = accessor.getSessionAttributes().get(SESSION_ID_ATTR);
        return v != null ? v.toString() : null;
    }

    private static String extractToken(StompHeaderAccessor accessor) {
        String auth = firstHeader(accessor, "Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String t = auth.substring(7).trim();
            return t.isEmpty() ? null : t;
        }
        String alt = firstHeader(accessor, "X-Auth-Token");
        if (alt != null && !alt.isBlank()) {
            return alt.trim();
        }
        return null;
    }

    private static String firstHeader(StompHeaderAccessor accessor, String name) {
        String v = accessor.getFirstNativeHeader(name);
        if (v != null) return v;
        // Some clients lower-case native headers.
        return accessor.getFirstNativeHeader(name.toLowerCase());
    }
}
