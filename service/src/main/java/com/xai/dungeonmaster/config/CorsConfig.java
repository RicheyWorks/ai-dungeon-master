package com.xai.dungeonmaster.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Global CORS policy driven by {@code game.cors.allowed-origins}.
 * Comma-separated origin patterns ({@code *} allowed in dev only).
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter(
            @Value("${game.cors.allowed-origins:*}") String allowedOrigins,
            @Value("${game.cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}") String allowedMethods,
            @Value("${game.cors.allowed-headers:*}") String allowedHeaders,
            @Value("${game.cors.max-age-seconds:3600}") long maxAgeSeconds) {

        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = split(allowedOrigins);
        if (origins.isEmpty() || (origins.size() == 1 && "*".equals(origins.get(0)))) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            // Patterns support https://*.example.com style
            config.setAllowedOriginPatterns(origins);
        }
        config.setAllowedMethods(split(allowedMethods));
        List<String> headers = split(allowedHeaders);
        if (headers.isEmpty() || (headers.size() == 1 && "*".equals(headers.get(0)))) {
            config.addAllowedHeader("*");
        } else {
            config.setAllowedHeaders(headers);
        }
        config.setExposedHeaders(List.of(
                "X-Request-Id",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining",
                "Retry-After"));
        config.setAllowCredentials(false);
        config.setMaxAge(Math.max(0L, maxAgeSeconds));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    /** Visible for WebSocket endpoint registration. */
    public static String[] originPatterns(String allowedOrigins) {
        List<String> list = split(allowedOrigins);
        if (list.isEmpty()) return new String[]{"*"};
        return list.toArray(String[]::new);
    }
}
