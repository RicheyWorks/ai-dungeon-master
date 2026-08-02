package com.xai.dungeonmaster.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
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
 * When {@code game.auth.enabled=true}, CONNECT without a valid JWT is rejected.
 * When auth is off, unauthenticated connections use the process-default engine.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** WebSocket session attribute holding the player session id (String). */
    public static final String SESSION_ID_ATTR = "dm.sessionId";

    private final JwtService jwt;
    private final SessionService sessions;
    private final boolean authRequired;

    @org.springframework.beans.factory.annotation.Autowired
    public StompAuthChannelInterceptor(
            JwtService jwt,
            SessionService sessions,
            @Value("${game.auth.enabled:false}") boolean authRequired) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.authRequired = authRequired;
    }

    /** Test helper. */
    public StompAuthChannelInterceptor(JwtService jwt, SessionService sessions) {
        this(jwt, sessions, false);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            boolean bound = false;
            if (token != null) {
                Optional<Map<String, Object>> claims = jwt.verify(token);
                if (claims.isPresent()) {
                    Object sub = claims.get().get("sub");
                    if (sub != null) {
                        Optional<SessionService.Session> session = sessions.touch(sub.toString());
                        if (session.isPresent() && accessor.getSessionAttributes() != null) {
                            accessor.getSessionAttributes().put(SESSION_ID_ATTR, session.get().id());
                            bound = true;
                        }
                    }
                }
            }
            if (authRequired && !bound) {
                throw new MessageDeliveryException(
                        "STOMP CONNECT requires Authorization: Bearer <jwt> when game.auth.enabled=true");
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
        return accessor.getFirstNativeHeader(name.toLowerCase());
    }
}
