package com.xai.dungeonmaster.web;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards that the static mod-browser page ships on the classpath and stays wired
 * to the catalog endpoint it renders. (Its render logic is covered separately by
 * a jsdom test.)
 */
class ModBrowserPageTest {

    @Test
    void pageShipsAndTargetsCatalogEndpoint() throws Exception {
        String html;
        try (InputStream is = getClass().getResourceAsStream("/static/mod-browser.html")) {
            assertNotNull(is, "mod-browser.html should ship as a static resource");
            html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(html.contains("/v2/catalog"), "page should call the catalog endpoint");
        assertTrue(html.contains("function renderCatalog"), "page should define renderCatalog");
        assertTrue(html.contains("function togglePack"), "page should define the enable/disable toggle");
        assertTrue(html.contains("/v2/catalog/packs/"), "page should call the pack toggle endpoints");
        for (String marker : new String[] {"id=\"packs\"", "id=\"plugins\"", "id=\"narration\"", "id=\"reload\""}) {
            assertTrue(html.contains(marker), "page should contain element " + marker);
        }
    }
}
