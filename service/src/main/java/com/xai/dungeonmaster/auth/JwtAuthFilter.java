package com.xai.dungeonmaster.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the caller's {@link SessionService.Session} from a
 * {@code Authorization: Bearer <jwt>} header and, when enforcement is enabled,
 * guards the versioned {@code /v2/**} endpoints.
 *
 * Behaviour:
 *   - Always attaches the resolved session as the {@link #SESSION_ATTR} request
 *     attribute when a valid token is present (so controllers can read identity
 *     even when enforcement is off).
 *   - When {@code game.auth.enabled=true}, any {@code /v2/**} request other than
 *     the public login endpoint ({@code POST /v2/session}) must carry a valid
 *     token; otherwise the filter short-circuits with a 401 error envelope.
 *   - When {@code game.auth.enabled=false} (the default), nothing is blocked —
 *     identity is best-effort. This keeps existing clients and tests working
 *     while the capability is opt-in.
 *
 * The legacy {@code /api/**} API and the WebSocket handshake are never gated.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Request attribute under which the authenticated session is exposed to controllers. */
    public static final String SESSION_ATTR = "dm.session";

    private static final String LOGIN_PATH = "/v2/session";

    private final JwtService jwt;
    private final SessionService sessions;
    private final boolean authEnabled;

    public JwtAuthFilter(JwtService jwt, SessionService sessions,
                         @Value("${game.auth.enabled:false}") boolean authEnabled) {
        this.jwt = jwt;
        this.sessions = sessions;
        this.authEnabled = authEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        SessionService.Session session = null;
        String token = bearer(req);
        if (token != null) {
            Optional<Map<String, Object>> claims = jwt.verify(token);
            if (claims.isPresent()) {
                Object sub = claims.get().get("sub");
                if (sub != null) {
                    session = sessions.touch(sub.toString()).orElse(null);
                }
            }
        }
        if (session != null) {
            req.setAttribute(SESSION_ATTR, session);
        }

        if (authEnabled && requiresAuth(req) && session == null) {
            writeUnauthorized(req, res);
            return;
        }
        chain.doFilter(req, res);
    }

    /** Auth applies to /v2/** except the public login endpoint. */
    private boolean requiresAuth(HttpServletRequest req) {
        String path = req.getRequestURI();
        if (path == null || !path.startsWith("/v2")) {
            return false;
        }
        return !path.equals(LOGIN_PATH);
    }

    private static void writeUnauthorized(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json");
        String requestId = safeRequestId(req);
        res.getWriter().write("{\"type\":\"error\",\"version\":1,\"payload\":{\"message\":"
                + "\"Authentication required. POST /v2/session to obtain a token.\"},"
                + "\"requestId\":\"" + requestId + "\"}");
    }

    private static String bearer(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String t = h.substring(7).trim();
            return t.isEmpty() ? null : t;
        }
        return null;
    }

    private static String safeRequestId(HttpServletRequest req) {
        String id = req.getHeader("X-Request-Id");
        if (id == null || id.isBlank()) {
            return "";
        }
        return id.replace("\"", "").replace("\\", "");
    }
}
