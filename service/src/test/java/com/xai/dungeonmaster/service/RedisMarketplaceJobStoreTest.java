package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.store.MemoryRedisOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisMarketplaceJobStoreTest {

    @Test
    void saveLoadRoundTripAcrossClients() {
        MemoryRedisOps redis = new MemoryRedisOps();
        RedisMarketplaceJobStore a = new RedisMarketplaceJobStore(redis, "dm", 3600);
        RedisMarketplaceJobStore b = new RedisMarketplaceJobStore(redis, "dm", 3600);

        MarketplaceJobStore.JobRecord rec = new MarketplaceJobStore.JobRecord(
                "job-1",
                "pack-a",
                "DOWNLOADING",
                1024,
                4096,
                "Downloading…",
                false,
                null,
                System.currentTimeMillis(),
                "session-owner-1");
        a.save(rec);

        MarketplaceJobStore.JobRecord loaded = b.load("job-1").orElseThrow();
        assertEquals("pack-a", loaded.packId());
        assertEquals("DOWNLOADING", loaded.phase());
        assertEquals(1024, loaded.bytesRead());
        assertEquals(4096, loaded.bytesTotal());
        assertEquals("session-owner-1", loaded.ownerSessionId());
        assertTrue(loaded.ownedBy("session-owner-1"));
        assertFalse(loaded.ownedBy("other"));
        assertFalse(loaded.cancelRequested());
        assertTrue(b.ids().contains("job-1"));
    }

    @Test
    void cancelFlagVisibleToOtherNode() {
        MemoryRedisOps redis = new MemoryRedisOps();
        RedisMarketplaceJobStore a = new RedisMarketplaceJobStore(redis, "t", 600);
        a.save(new MarketplaceJobStore.JobRecord(
                "j2", "p", "DOWNLOADING", 0, 0, "go", false, null, System.currentTimeMillis(), null));
        a.save(new MarketplaceJobStore.JobRecord(
                "j2", "p", "CANCELLED", 0, 0, "Cancel requested", true, null, System.currentTimeMillis(), null));

        MarketplaceJobStore.JobRecord loaded = new RedisMarketplaceJobStore(redis, "t", 600)
                .load("j2").orElseThrow();
        assertEquals("CANCELLED", loaded.phase());
        assertTrue(loaded.cancelRequested());
        assertNull(loaded.ownerSessionId());
        assertTrue(loaded.ownedBy("anyone"));
    }

    @Test
    void servicePersistsProgressToSharedStore() throws Exception {
        MemoryRedisOps redis = new MemoryRedisOps();
        MarketplaceJobStore store = new RedisMarketplaceJobStore(redis, "svc", 3600);

        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("mkt-job-redis");
        java.nio.file.Path local = tmp.resolve("local");
        java.nio.file.Files.createDirectories(local);
        java.nio.file.Path installDir = tmp.resolve("installed");
        java.nio.file.Files.createDirectories(installDir);

        java.nio.file.Path zip = tmp.resolve("p.zip");
        try (java.util.zip.ZipOutputStream zos =
                     new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(zip))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("pack.yaml"));
            zos.write("""
                    id: "redis-job-pack"
                    displayName: "Redis Job Pack"
                    version: "1.0.0"
                    minEngineVersion: "1.0.0"
                    description: "x"
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        String sha = MarketplaceIntegrity.sha256Hex(java.nio.file.Files.readAllBytes(zip));
        java.nio.file.Path index = tmp.resolve("index.json");
        java.nio.file.Files.writeString(index, """
                {"version":1,"packs":[{
                  "id":"redis-job-pack","displayName":"R","version":"1.0.0",
                  "downloadUrl":"%s","sha256":"%s"
                }]}
                """.formatted(zip.toAbsolutePath().toString().replace("\\", "\\\\"), sha));

        PackUploadService uploads = new PackUploadService(installDir.toString());
        MarketplaceService svc = new MarketplaceService(
                local, index.toString(), 0, false, "", uploads, store);

        var started = svc.startInstallAsync("redis-job-pack");
        // other "node" only has the store
        MarketplaceService other = new MarketplaceService(
                local, index.toString(), 0, false, "", uploads, store);

        long deadline = System.currentTimeMillis() + 10_000;
        com.xai.dungeonmaster.dto.MarketplaceInstallJob seen = null;
        while (System.currentTimeMillis() < deadline) {
            seen = other.job(started.jobId()).orElse(null);
            if (seen != null && ("DONE".equals(seen.phase())
                    || "FAILED".equals(seen.phase())
                    || "CANCELLED".equals(seen.phase()))) {
                break;
            }
            Thread.sleep(20);
        }
        assertNotNull(seen);
        // Worker still local to first svc — other node may mark FAILED if polled after worker finished
        // and local map empty... actually first svc keeps job in memory; store should have DONE.
        MarketplaceJobStore.JobRecord durable = store.load(started.jobId()).orElseThrow();
        assertEquals("DONE", durable.phase(), durable.message());
    }
}
