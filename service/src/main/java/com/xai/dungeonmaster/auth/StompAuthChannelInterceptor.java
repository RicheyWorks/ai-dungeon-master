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
 * Binds an authenticated player session to a STOMP connection on CONNECT and
 * enforces destination ACLs so one session cannot subscribe to another's
 * narrative topic or send to arbitrary destinations.
 *
 * Clients pass {@code Authorization: Bearer <jwt>} (or {@code X-Auth-Token})
 * as a STOMP native header when connecting. On success the session id is stored
 * under {@link #SESSION_ID_ATTR}.
 *
 * When {@code game.auth.enabled=true}, CONNECT without a valid JWT is rejected
 * and SUBSCRIBE is limited to that session's narrative topic.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /** WebSocket session attribute holding the player session id (String). */
    public static final String SESSION_ID_ATTR = "dm.sessionId";

    private static final String LEGACY_NARRATIVE = "/topic/narrative";
    private static final String NARRATIVE_PREFIX = "/topic/narrative/";
    private static final String APP_ACTION = "/app/action";
    private static final String APP_NARRATE = "/app/narrate";

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
        StompCommand cmd = accessor.getCommand();
        if (StompCommand.CONNECT.equals(cmd)) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(cmd)) {
            handleSubscribe(accessor);
        } else if (StompCommand.SEND.equals(cmd)) {
            handleSend(accessor);
        }
        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
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

    /**
     * Session-bound clients may only subscribe to {@code /topic/narrative/{sessionId}}
     * (or the legacy process-wide {@code /topic/narrative} when auth is off).
     */
    private void handleSubscribe(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null || dest.isBlank()) {
            throw new MessageDeliveryException("SUBSCRIBE requires a destination");
        }
        String sessionId = sessionIdOf(accessor);

        if (LEGACY_NARRATIVE.equals(dest)) {
            // Process-default stream — only when auth is off (single-tenant / tests).
            if (authRequired) {
                throw new MessageDeliveryException(
                        "SUBSCRIBE to shared /topic/narrative is disabled when auth is required; "
                                + "use /topic/narrative/{sessionId}");
            }
            return;
        }

        if (dest.startsWith(NARRATIVE_PREFIX)) {
            String topicSession = dest.substring(NARRATIVE_PREFIX.length());
            if (topicSession.isBlank() || topicSession.contains("/") || topicSession.contains("..")) {
                throw new MessageDeliveryException("Invalid narrative topic");
            }
            if (authRequired) {
                if (sessionId == null || sessionId.isBlank()) {
                    throw new MessageDeliveryException("SUBSCRIBE requires an authenticated session");
                }
                if (!sessionId.equals(topicSession)) {
                    throw new MessageDeliveryException(
                            "SUBSCRIBE denied: cannot listen to another session's narrative");
                }
            } else if (sessionId != null && !sessionId.isBlank() && !sessionId.equals(topicSession)) {
                // Even with auth off, a bound connection must not cross sessions.
                throw new MessageDeliveryException(
                        "SUBSCRIBE denied: cannot listen to another session's narrative");
            }
            return;
        }

        throw new MessageDeliveryException("SUBSCRIBE destination not allowed: " + dest);
    }

    private void handleSend(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null) {
            return;
        }
        if (APP_ACTION.equals(dest) || APP_NARRATE.equals(dest)) {
            if (authRequired && (sessionIdOf(accessor) == null || sessionIdOf(accessor).isBlank())) {
                throw new MessageDeliveryException("SEND requires an authenticated session");
            }
            return;
        }
        // Allow other app destinations only when auth is off (legacy flexibility).
        if (authRequired) {
            throw new MessageDeliveryException("SEND destination not allowed: " + dest);
        }
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
