package com.xai.dungeonmaster.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceDownloadCapTest {

    @Test
    void rejectsLocalFileOverCap(@TempDir Path tmp) throws Exception {
        Path big = tmp.resolve("huge.bin");
        Files.write(big, new byte[2048]);
        PackUploadService uploads = new PackUploadService(tmp.resolve("packs").toString());
        MarketplaceService svc = new MarketplaceService(
                tmp, "", 0, false, "", uploads, new MemoryMarketplaceJobStore(),
                java.net.http.HttpClient.newHttpClient(), 1024);

        Method m = MarketplaceService.class.getDeclaredMethod("downloadBytes", String.class);
        m.setAccessible(true);
        Exception ex = assertThrows(Exception.class, () -> {
            try {
                m.invoke(svc, big.toString());
            } catch (java.lang.reflect.InvocationTargetException ite) {
                throw (Exception) ite.getCause();
            }
        });
        assertTrue(ex.getMessage().contains("exceeds max"), ex.getMessage());
    }
}
