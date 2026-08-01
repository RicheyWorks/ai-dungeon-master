package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.dto.MarketplaceListing;
import com.xai.dungeonmaster.dto.MarketplacePayload;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceServiceTest {

    @TempDir Path tmp;

    @BeforeEach
    void setUp() {
        ContentRegistry.clearForTests();
    }

    @AfterEach
    void tearDown() {
        ContentRegistry.clearForTests();
    }

    @Test
    void listsPacksFromFilesystem() throws Exception {
        writePack(tmp.resolve("demo-pack"), "demo-pack", "Demo Pack", "A test pack for marketplace.");
        writePack(tmp.resolve("other-pack"), "other-pack", "Other", "Second pack.");

        MarketplaceService svc = new MarketplaceService(tmp);
        MarketplacePayload payload = svc.list(null);
        assertEquals(2, payload.available());
        assertEquals(0, payload.installed());
        assertTrue(payload.packs().stream().anyMatch(p -> p.id().equals("demo-pack")));
        assertFalse(payload.packs().get(0).installed());
    }

    @Test
    void searchFiltersByQuery() throws Exception {
        writePack(tmp.resolve("demo-pack"), "demo-pack", "Demo Pack", "gothic horror drowned parish");
        writePack(tmp.resolve("other-pack"), "other-pack", "Sunny Fields", "cheerful meadow");

        MarketplaceService svc = new MarketplaceService(tmp);
        MarketplacePayload horror = svc.list("gothic");
        assertEquals(1, horror.available());
        assertEquals("demo-pack", horror.packs().get(0).id());
    }

    @Test
    void installRegistersPack() throws Exception {
        writePack(tmp.resolve("demo-pack"), "demo-pack", "Demo Pack", "install me");
        MarketplaceService svc = new MarketplaceService(tmp);

        MarketplaceService.InstallResult r = svc.install("demo-pack");
        assertTrue(r.ok());
        assertFalse(r.alreadyInstalled());
        assertTrue(ContentRegistry.isKnown("demo-pack"));
        assertTrue(ContentRegistry.isEnabled("demo-pack"));

        MarketplaceListing listing = svc.get("demo-pack").orElseThrow();
        assertTrue(listing.installed());
        assertTrue(listing.enabled());

        MarketplaceService.InstallResult again = svc.install("demo-pack");
        assertTrue(again.ok());
        assertTrue(again.alreadyInstalled());
    }

    private static void writePack(Path dir, String id, String name, String description) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pack.yaml"), """
                id: "%s"
                displayName: "%s"
                version: "1.0.0"
                minEngineVersion: "1.0.0"
                description: "%s"
                """.formatted(id, name, description));
        Files.createDirectories(dir.resolve("items"));
        Files.createDirectories(dir.resolve("monsters"));
    }
}
