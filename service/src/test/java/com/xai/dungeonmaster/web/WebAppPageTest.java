package com.xai.dungeonmaster.web;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards that the Vite SPA is staged under {@code classpath:/static/app/}
 * (via {@code scripts/build-web.sh}) so the fat jar serves the full client
 * at {@code /app/}.
 */
class WebAppPageTest {

    @Test
    void spaIndexShipsWithAppBaseAssets() throws Exception {
        String html;
        try (InputStream is = getClass().getResourceAsStream("/static/app/index.html")) {
            assertNotNull(is, "static/app/index.html should ship — run scripts/build-web.sh");
            html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(html.contains("/app/assets/"), "production assets must be rooted at /app/assets/");
        assertTrue(html.contains("id=\"root\""), "React mount point required");

        try (InputStream marker = getClass().getResourceAsStream("/static/app/.built-from")) {
            assertNotNull(marker, ".built-from marker should be staged with the SPA");
        }
    }
}
