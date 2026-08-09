package com.xai.dungeonmaster.config;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Aligns Tomcat connector post/swallow limits with {@code game.http.max-request-bytes}
 * as defense-in-depth behind {@link RequestSizeFilter}. Multipart pack uploads still
 * use the higher {@code spring.servlet.multipart.*} limits (Tomcat swallows only after
 * the connector accepts the request; multipart max is enforced by Spring).
 */
@Component
public class TomcatBodyLimitCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final int maxBytes;

    public TomcatBodyLimitCustomizer(
            @Value("${game.http.max-request-bytes:1048576}") long maxRequestBytes) {
        // Connector maxPostSize is int; clamp to at least 256 and at most Integer.MAX
        long clamped = Math.min(Integer.MAX_VALUE, Math.max(256L, maxRequestBytes));
        this.maxBytes = (int) clamped;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector -> {
            // maxPostSize: FORM parameter parsing cap (also used as a coarse post limit)
            connector.setMaxPostSize(maxBytes);
            // maxSavePostSize: size of POST saved during AUTH / FORM resubmit
            connector.setMaxSavePostSize(maxBytes);
            if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol<?> proto) {
                // How much of an aborted/rejected request body Tomcat will read and discard
                proto.setMaxSwallowSize(maxBytes);
            }
        });
    }
}
