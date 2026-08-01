package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.dto.MarketplaceInstallJob;
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

class MarketplaceInstallJobTest {

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
    void asyncInstallCompletesWithProgress() throws Exception {
        Path local = tmp.resolve("local");
        Files.createDirectories(local);
        Path installDir = tmp.resolve("installed");
        Files.createDirectories(installDir);

        Path zip = tmp.resolve("job-pack.zip");
        writePackZip(zip, "job-pack", "Job Pack", "async");
        String sha = MarketplaceIntegrity.sha256Hex(Files.readAllBytes(zip));
        Path index = tmp.resolve("index.json");
        Files.writeString(index, """
                {
                  "version": 1,
                  "packs": [{
                    "id": "job-pack",
                    "displayName": "Job Pack",
                    "version": "1.0.0",
                    "downloadUrl": "%s",
                    "sha256": "%s"
                  }]
                }
                """.formatted(zip.toAbsolutePath().toString().replace("\\", "\\\\"), sha));

        PackUploadService uploads = new PackUploadService(installDir.toString());
        MarketplaceService svc = new MarketplaceService(local, index.toString(), 0, false, "", uploads);

        MarketplaceInstallJob started = svc.startInstallAsync("job-pack");
        assertNotNull(started.jobId());
        assertEquals("job-pack", started.packId());

        MarketplaceInstallJob done = awaitTerminal(svc, started.jobId(), 10_000);
        assertEquals("DONE", done.phase(), done.message());
        assertTrue(done.percent() >= 0);
        assertTrue(ContentRegistry.isKnown("job-pack"));
    }

    @Test
    void cancelUnknownJobReturnsFalse() {
        PackUploadService uploads = new PackUploadService(tmp.resolve("i").toString());
        MarketplaceService svc = new MarketplaceService(tmp.resolve("l"), "", 0, uploads);
        assertFalse(svc.cancelJob("nope"));
    }

    private static MarketplaceInstallJob awaitTerminal(MarketplaceService svc, String jobId, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        MarketplaceInstallJob last = null;
        while (System.currentTimeMillis() < deadline) {
            last = svc.job(jobId).orElseThrow();
            if ("DONE".equals(last.phase()) || "FAILED".equals(last.phase()) || "CANCELLED".equals(last.phase())) {
                return last;
            }
            Thread.sleep(20);
        }
        fail("job did not finish: " + last);
        return last;
    }

    private static void writePackZip(Path zip, String id, String name, String description) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("pack.yaml"));
            zos.write("""
                    id: "%s"
                    displayName: "%s"
                    version: "1.0.0"
                    minEngineVersion: "1.0.0"
                    description: "%s"
                    """.formatted(id, name, description).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("items/"));
            zos.closeEntry();
        }
    }
}
