package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.dto.MarketplaceInstallJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceJobOwnershipTest {

    @TempDir
    Path tmp;

    @Test
    void jobRecordCarriesOwnerAndAcl() throws Exception {
        Path packs = tmp.resolve("packs");
        Files.createDirectories(packs);
        // local listing via empty dir is fine; startInstall needs known listing
        // Use service with a remote/local fixture — create a local pack dir with pack.yaml
        Path local = packs.resolve("own-pack");
        Files.createDirectories(local);
        Files.writeString(local.resolve("pack.yaml"), """
                id: own-pack
                displayName: Own Pack
                version: 1.0.0
                """);

        PackUploadService uploads = new PackUploadService(tmp.resolve("installed").toString());
        MarketplaceService svc = new MarketplaceService(packs, "", 0, uploads);

        MarketplaceInstallJob job = svc.startInstallAsync("own-pack", "session-A");
        var rec = svc.jobRecord(job.jobId()).orElseThrow();
        assertEquals("session-A", rec.ownerSessionId());
        assertTrue(rec.ownedBy("session-A"));
        assertFalse(rec.ownedBy("session-B"));
        assertFalse(rec.ownedBy(null));
    }

    @Test
    void nullOwnerIsClosed() {
        MarketplaceJobStore.JobRecord rec = new MarketplaceJobStore.JobRecord(
                "j1", "p", "QUEUED", 0, 0, "q", false, null, System.currentTimeMillis(), null);
        assertFalse(rec.ownedBy(null));
        assertFalse(rec.ownedBy("anyone"));
    }
}
