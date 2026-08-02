package com.xai.dungeonmaster.content;

import com.xai.dungeonmaster.auth.JwtAuthFilter;
import com.xai.dungeonmaster.auth.SessionService;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Installs the caller's session pack-enable overlay on {@link ContentRegistry}
 * for the duration of the request so loot/encounter generation is multi-tenant.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class SessionContentFilter extends OncePerRequestFilter {

    private final SessionPackService packs;

    public SessionContentFilter(SessionPackService packs) {
        this.packs = packs;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!packs.isSessionScoped()) {
            chain.doFilter(request, response);
            return;
        }
        Object attr = request.getAttribute(JwtAuthFilter.SESSION_ATTR);
        if (!(attr instanceof SessionService.Session session)
                || session.id() == null
                || session.id().isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        ContentRegistry.pushEnabledOverride(packs.enabledPackIds(session.id()));
        try {
            chain.doFilter(request, response);
        } finally {
            ContentRegistry.clearEnabledOverride();
        }
    }
}
