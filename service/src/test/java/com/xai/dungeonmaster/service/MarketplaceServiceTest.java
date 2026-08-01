package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.dto.MarketplaceListing;
import com.xai.dungeonmaster.dto.MarketplacePayload;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    private MarketplaceService localOnly() {
        PackUploadService uploads = new PackUploadService(tmp.resolve("installed").toString());
        return new MarketplaceService(tmp.resolve("local"), "", 0, uploads);
    }

    @Test
    void listsPacksFromFilesystem() throws Exception {
        Path local = tmp.resolve("local");
        writePack(local.resolve("demo-pack"), "demo-pack", "Demo Pack", "A test pack for marketplace.");
        writePack(local.resolve("other-pack"), "other-pack", "Other", "Second pack.");

        MarketplaceService svc = localOnly();
        MarketplacePayload payload = svc.list(null);
        assertEquals(2, payload.available());
        assertEquals(0, payload.installed());
        assertTrue(payload.packs().stream().anyMatch(p -> p.id().equals("demo-pack")));
        assertFalse(payload.packs().get(0).installed());
        assertEquals("local", payload.packs().get(0).source());
    }

    @Test
    void searchFiltersByQuery() throws Exception {
        Path local = tmp.resolve("local");
        writePack(local.resolve("demo-pack"), "demo-pack", "Demo Pack", "gothic horror drowned parish");
        writePack(local.resolve("other-pack"), "other-pack", "Sunny Fields", "cheerful meadow");

        MarketplaceService svc = localOnly();
        MarketplacePayload horror = svc.list("gothic");
        assertEquals(1, horror.available());
        assertEquals("demo-pack", horror.packs().get(0).id());
    }

    @Test
    void installRegistersPack() throws Exception {
        Path local = tmp.resolve("local");
        writePack(local.resolve("demo-pack"), "demo-pack", "Demo Pack", "install me");
        MarketplaceService svc = localOnly();

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

    @Test
    void mergesRemoteIndexAndInstallsZipWithChecksum() throws Exception {
        Path local = tmp.resolve("local");
        Files.createDirectories(local);
        Path installDir = tmp.resolve("installed");
        Files.createDirectories(installDir);

        Path zip = tmp.resolve("remote-pack.zip");
        writePackZip(zip, "remote-pack", "Remote Pack", "from index");
        byte[] zipBytes = Files.readAllBytes(zip);
        String sha = MarketplaceIntegrity.sha256Hex(zipBytes);

        Path index = tmp.resolve("index.json");
        Files.writeString(index, """
                {
                  "version": 1,
                  "packs": [
                    {
                      "id": "remote-pack",
                      "displayName": "Remote Pack",
                      "version": "2.0.0",
                      "minEngineVersion": "1.0.0",
                      "description": "from index",
                      "downloadUrl": "%s",
                      "sha256": "%s"
                    }
                  ]
                }
                """.formatted(zip.toAbsolutePath().toString().replace("\\", "\\\\"), sha));

        PackUploadService uploads = new PackUploadService(installDir.toString());
        MarketplaceService svc = new MarketplaceService(local, index.toString(), 0, true, "", uploads);

        MarketplacePayload payload = svc.list(null);
        assertEquals(1, payload.available());
        assertTrue(payload.remoteOk());
        assertEquals("remote", payload.packs().get(0).source());
        assertEquals(sha, payload.packs().get(0).sha256());

        MarketplaceService.InstallResult r = svc.install("remote-pack");
        assertTrue(r.ok(), r.message());
        assertTrue(ContentRegistry.isKnown("remote-pack"));
    }

    @Test
    void rejectsChecksumMismatch() throws Exception {
        Path local = tmp.resolve("local");
        Files.createDirectories(local);
        Path installDir = tmp.resolve("installed");
        Files.createDirectories(installDir);

        Path zip = tmp.resolve("bad-pack.zip");
        writePackZip(zip, "bad-pack", "Bad Pack", "tampered");
        Path index = tmp.resolve("index-bad.json");
        Files.writeString(index, """
                {
                  "version": 1,
                  "packs": [
                    {
                      "id": "bad-pack",
                      "displayName": "Bad Pack",
                      "version": "1.0.0",
                      "downloadUrl": "%s",
                      "sha256": "%s"
                    }
                  ]
                }
                """.formatted(
                zip.toAbsolutePath().toString().replace("\\", "\\\\"),
                "0".repeat(64)));

        PackUploadService uploads = new PackUploadService(installDir.toString());
        MarketplaceService svc = new MarketplaceService(local, index.toString(), 0, false, "", uploads);

        MarketplaceService.InstallResult r = svc.install("bad-pack");
        assertFalse(r.ok());
        assertTrue(r.message().contains("SHA-256 mismatch"), r.message());
        assertFalse(ContentRegistry.isKnown("bad-pack"));
    }

    @Test
    void verifiesIndexHmac() throws Exception {
        Path local = tmp.resolve("local");
        Files.createDirectories(local);
        Path installDir = tmp.resolve("installed");
        Files.createDirectories(installDir);

        Path zip = tmp.resolve("signed-pack.zip");
        writePackZip(zip, "signed-pack", "Signed", "ok");
        String sha = MarketplaceIntegrity.sha256Hex(Files.readAllBytes(zip));

        String unsigned = """
                {
                  "version": 1,
                  "packs": [
                    {
                      "id": "signed-pack",
                      "displayName": "Signed",
                      "version": "1.0.0",
                      "downloadUrl": "%s",
                      "sha256": "%s"
                    }
                  ]
                }
                """.formatted(zip.toAbsolutePath().toString().replace("\\", "\\\\"), sha);

        // Signature over Jackson-stripped form: parse and re-serialize without signature
        byte[] canonical = MarketplaceService.stripJsonSignatureField(
                unsigned.getBytes(StandardCharsets.UTF_8));
        String secret = "index-secret";
        String sig = MarketplaceIntegrity.hmacSha256Hex(canonical, secret);

        // Embed signature field (verification strips it and re-serializes via Jackson)
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(unsigned);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("signature", sig);
        Path index = tmp.resolve("signed-index.json");
        Files.write(index, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(node));

        PackUploadService uploads = new PackUploadService(installDir.toString());
        MarketplaceService ok = new MarketplaceService(local, index.toString(), 0, false, secret, uploads);
        assertTrue(ok.list(null).remoteOk());
        assertEquals(1, ok.list(null).available());

        MarketplaceService bad = new MarketplaceService(local, index.toString(), 0, false, "wrong", uploads);
        assertFalse(bad.list(null).remoteOk());
        assertTrue(bad.list(null).remoteError().contains("HMAC"), bad.list(null).remoteError());
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

    private static void writePackZip(Path zip, String id, String name, String description) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("pack.yaml"));
            String yaml = """
                    id: "%s"
                    displayName: "%s"
                    version: "2.0.0"
                    minEngineVersion: "1.0.0"
                    description: "%s"
                    """.formatted(id, name, description);
            zos.write(yaml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("items/"));
            zos.closeEntry();
        }
    }
}
