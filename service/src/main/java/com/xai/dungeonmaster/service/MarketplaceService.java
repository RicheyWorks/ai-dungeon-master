package com.xai.dungeonmaster.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xai.dungeonmaster.dto.MarketplaceListing;
import com.xai.dungeonmaster.dto.MarketplacePayload;
import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Content-pack marketplace: local filesystem discovery plus optional remote
 * index JSON ({@code game.marketplace.remote-url}).
 *
 * Remote index shape:
 * <pre>
 * {
 *   "version": 1,
 *   "packs": [
 *     {
 *       "id": "extra-pack",
 *       "displayName": "Extra Pack",
 *       "version": "1.0.0",
 *       "minEngineVersion": "1.0.0",
 *       "description": "…",
 *       "downloadUrl": "https://example.com/packs/extra-pack.zip"
 *     }
 *   ]
 * }
 * </pre>
 *
 * Install: local packs register from disk; remote packs download the zip and
 * go through {@link PackUploadService}.
 */
@Service
public class MarketplaceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path root;
    private final String remoteUrl;
    private final long cacheTtlMs;
    private final PackUploadService uploads;
    private final HttpClient http;

    private final AtomicReference<CachedRemote> remoteCache = new AtomicReference<>();

    public MarketplaceService(
            @Value("${game.content.packs.dir:content-packs}") String contentPacksDir,
            @Value("${game.marketplace.remote-url:}") String remoteUrl,
            @Value("${game.marketplace.remote-cache-seconds:300}") long cacheSeconds,
            PackUploadService uploads) {
        this.root = Paths.get(contentPacksDir).toAbsolutePath().normalize();
        this.remoteUrl = remoteUrl == null ? "" : remoteUrl.trim();
        this.cacheTtlMs = Math.max(0L, cacheSeconds) * 1000L;
        this.uploads = uploads;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Visible for tests. */
    public MarketplaceService(Path root, String remoteUrl, long cacheSeconds, PackUploadService uploads) {
        this.root = root.toAbsolutePath().normalize();
        this.remoteUrl = remoteUrl == null ? "" : remoteUrl.trim();
        this.cacheTtlMs = Math.max(0L, cacheSeconds) * 1000L;
        this.uploads = uploads;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Visible for tests with custom HTTP client. */
    MarketplaceService(Path root, String remoteUrl, long cacheSeconds, PackUploadService uploads, HttpClient http) {
        this.root = root.toAbsolutePath().normalize();
        this.remoteUrl = remoteUrl == null ? "" : remoteUrl.trim();
        this.cacheTtlMs = Math.max(0L, cacheSeconds) * 1000L;
        this.uploads = uploads;
        this.http = http;
    }

    public Path root() {
        return root;
    }

    public MarketplacePayload list(String query) {
        List<MarketplaceListing> packs = mergedListings();
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase(Locale.ROOT);
            packs = packs.stream()
                    .filter(p -> contains(p.id(), q)
                            || contains(p.displayName(), q)
                            || contains(p.description(), q))
                    .toList();
        }
        int installed = (int) packs.stream().filter(MarketplaceListing::installed).count();
        RemoteSnapshot remote = remoteSnapshot();
        return new MarketplacePayload(
                root.toString(),
                remoteUrl.isBlank() ? null : remoteUrl,
                remote.ok(),
                remote.error(),
                packs.size(),
                installed,
                packs);
    }

    public Optional<MarketplaceListing> get(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return mergedListings().stream().filter(p -> p.id().equalsIgnoreCase(id.trim())).findFirst();
    }

    /**
     * Install a marketplace pack into the live {@link ContentRegistry}.
     * Local: register from directory. Remote: download zip + {@link PackUploadService}.
     */
    public InstallResult install(String id) {
        if (id == null || id.isBlank()) {
            return InstallResult.fail("Missing pack id");
        }
        MarketplaceListing listing = get(id.trim()).orElse(null);
        if (listing == null) {
            return InstallResult.fail("Unknown marketplace pack: " + id.trim());
        }
        if (ContentRegistry.isKnown(listing.id())) {
            return InstallResult.already(listing.id());
        }
        if ("remote".equalsIgnoreCase(listing.source())) {
            return installRemote(listing);
        }
        Path dir = Paths.get(listing.sourcePath());
        ContentPack pack = ResourceLoader.loadAndRegisterPack(dir);
        if (pack == null) {
            return InstallResult.fail("Failed to load pack at " + dir);
        }
        ContentRegistry.setEnabled(pack.id(), true);
        return InstallResult.installed(pack.id());
    }

    private InstallResult installRemote(MarketplaceListing listing) {
        if (listing.downloadUrl() == null || listing.downloadUrl().isBlank()) {
            return InstallResult.fail("Remote pack missing downloadUrl: " + listing.id());
        }
        try {
            byte[] zip = downloadBytes(listing.downloadUrl());
            PackUploadService.InstalledPack installed = uploads.install(zip, false);
            ContentRegistry.setEnabled(installed.pack().id(), true);
            return InstallResult.installed(installed.pack().id());
        } catch (PackUploadService.PackUploadException e) {
            if (e.isConflict()) {
                return InstallResult.already(listing.id());
            }
            return InstallResult.fail(e.getMessage());
        } catch (Exception e) {
            return InstallResult.fail("Download failed: " + e.getMessage());
        }
    }

    private List<MarketplaceListing> mergedListings() {
        Map<String, MarketplaceListing> byId = new LinkedHashMap<>();
        for (MarketplaceListing local : scanLocal()) {
            byId.put(local.id().toLowerCase(Locale.ROOT), local);
        }
        for (MarketplaceListing remote : remoteSnapshot().packs()) {
            String key = remote.id().toLowerCase(Locale.ROOT);
            // Local disk wins when both present (already extracted)
            byId.putIfAbsent(key, remote);
        }
        List<MarketplaceListing> out = new ArrayList<>(byId.values());
        out.sort(Comparator.comparing(MarketplaceListing::id, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private List<MarketplaceListing> scanLocal() {
        List<MarketplaceListing> out = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;
                ResourceLoader.PackManifest meta = ResourceLoader.readPackManifest(dir);
                if (meta == null) continue;
                boolean installed = ContentRegistry.isKnown(meta.id);
                boolean enabled = installed && ContentRegistry.isEnabled(meta.id);
                out.add(MarketplaceListing.local(
                        meta.id,
                        meta.displayName != null ? meta.displayName : meta.id,
                        meta.version != null ? meta.version : "0.0.0",
                        meta.minEngineVersion != null ? meta.minEngineVersion : "1.0.0",
                        meta.description != null ? meta.description : "",
                        installed,
                        enabled,
                        dir.toString()));
            }
        } catch (IOException e) {
            System.err.println("[marketplace] local scan failed: " + e.getMessage());
        }
        return out;
    }

    private RemoteSnapshot remoteSnapshot() {
        if (remoteUrl.isBlank()) {
            return RemoteSnapshot.empty();
        }
        long now = System.currentTimeMillis();
        CachedRemote cached = remoteCache.get();
        if (cached != null && (cacheTtlMs == 0 || now - cached.fetchedAtMs <= cacheTtlMs)) {
            return cached.snapshot;
        }
        try {
            List<MarketplaceListing> packs = fetchRemoteIndex(remoteUrl);
            RemoteSnapshot snap = new RemoteSnapshot(true, null, packs);
            remoteCache.set(new CachedRemote(now, snap));
            return snap;
        } catch (Exception e) {
            RemoteSnapshot snap = new RemoteSnapshot(false, e.getMessage(), List.of());
            // short negative cache (30s) unless ttl is 0
            remoteCache.set(new CachedRemote(now - Math.max(0, cacheTtlMs - 30_000L), snap));
            return snap;
        }
    }

    private List<MarketplaceListing> fetchRemoteIndex(String url) throws Exception {
        byte[] body;
        if (url.startsWith("file:")) {
            body = Files.readAllBytes(Path.of(URI.create(url)));
        } else if (url.startsWith("/") || url.startsWith("./") || (!url.contains("://") && Files.exists(Paths.get(url)))) {
            body = Files.readAllBytes(Paths.get(url).toAbsolutePath().normalize());
        } else {
            body = downloadBytes(url);
        }
        JsonNode root = MAPPER.readTree(body);
        JsonNode packsNode = root.path("packs");
        if (!packsNode.isArray()) {
            throw new IllegalStateException("remote index missing packs[]");
        }
        List<MarketplaceListing> out = new ArrayList<>();
        for (JsonNode n : packsNode) {
            String id = text(n, "id");
            if (id == null || id.isBlank()) continue;
            String downloadUrl = text(n, "downloadUrl");
            if (downloadUrl == null || downloadUrl.isBlank()) continue;
            boolean installed = ContentRegistry.isKnown(id);
            boolean enabled = installed && ContentRegistry.isEnabled(id);
            out.add(MarketplaceListing.remote(
                    id,
                    textOr(n, "displayName", id),
                    textOr(n, "version", "0.0.0"),
                    textOr(n, "minEngineVersion", "1.0.0"),
                    textOr(n, "description", ""),
                    installed,
                    enabled,
                    downloadUrl));
        }
        return out;
    }

    private byte[] downloadBytes(String url) throws Exception {
        if (url.startsWith("file:")) {
            return Files.readAllBytes(Path.of(URI.create(url)));
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // treat as local path (tests / offline fixtures)
            Path p = Paths.get(url);
            if (Files.isRegularFile(p)) return Files.readAllBytes(p);
            throw new IllegalArgumentException("Unsupported download URL: " + url);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .header("Accept", "application/json, application/zip, */*")
                .build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("HTTP " + res.statusCode() + " for " + url);
        }
        try (InputStream in = res.body()) {
            return in.readAllBytes();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String textOr(JsonNode n, String field, String fallback) {
        String v = text(n, field);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    private record CachedRemote(long fetchedAtMs, RemoteSnapshot snapshot) {}

    private record RemoteSnapshot(boolean ok, String error, List<MarketplaceListing> packs) {
        static RemoteSnapshot empty() {
            return new RemoteSnapshot(true, null, List.of());
        }
    }

    public record InstallResult(boolean ok, boolean alreadyInstalled, String packId, String message) {
        static InstallResult installed(String id) {
            return new InstallResult(true, false, id, "Installed " + id);
        }
        static InstallResult already(String id) {
            return new InstallResult(true, true, id, "Already installed: " + id);
        }
        static InstallResult fail(String msg) {
            return new InstallResult(false, false, null, msg);
        }
    }
}
