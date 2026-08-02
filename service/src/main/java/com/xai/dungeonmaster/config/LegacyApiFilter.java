package com.xai.dungeonmaster.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Gates legacy {@code /api/game/**} endpoints. Disabled in production by default
 * ({@code game.legacy.api.enabled=false} under the prod profile).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class LegacyApiFilter extends OncePerRequestFilter {

    private final boolean enabled;

    public LegacyApiFilter(@Value("${game.legacy.api.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (enabled) return true;
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/game");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setStatus(410);
        res.setContentType("application/json");
        String requestId = RequestIdFilter.resolve(req);
        if (requestId == null) requestId = "";
        requestId = requestId.replace("\"", "").replace("\\", "");
        res.getWriter().write("{\"type\":\"error\",\"version\":1,\"payload\":{\"message\":"
                + "\"Legacy /api/game API is disabled. Use /v2/* with a Bearer session token.\"},"
                + "\"requestId\":\"" + requestId + "\"}");
    }
}
