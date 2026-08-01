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
 *   "signature": "<hmac-sha256 hex of body with signature field removed / or header>",
 *   "packs": [
 *     {
 *       "id": "extra-pack",
 *       "displayName": "Extra Pack",
 *       "version": "1.0.0",
 *       "minEngineVersion": "1.0.0",
 *       "description": "…",
 *       "downloadUrl": "https://example.com/packs/extra-pack.zip",
 *       "sha256": "…hex…"
 *     }
 *   ]
 * }
 * </pre>
 *
 * Integrity:
 * <ul>
 *   <li>Per-pack {@code sha256} verified after download (required when
 *       {@code game.marketplace.require-checksums=true})</li>
 *   <li>Optional HMAC-SHA256 of the raw index bytes via
 *       {@code game.marketplace.remote-hmac-secret} against header
 *       {@code X-Marketplace-Signature} or JSON field {@code signature}
 *       (when verifying JSON field, the field is stripped before HMAC)</li>
 * </ul>
 */
@Service
public class MarketplaceService {

    public static final String SIGNATURE_HEADER = "X-Marketplace-Signature";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path root;
    private final String remoteUrl;
    private final long cacheTtlMs;
    private final boolean requireChecksums;
    private final String hmacSecret;
    private final PackUploadService uploads;
    private final HttpClient http;

    private final AtomicReference<CachedRemote> remoteCache = new AtomicReference<>();

    public MarketplaceService(
            @Value("${game.content.packs.dir:content-packs}") String contentPacksDir,
            @Value("${game.marketplace.remote-url:}") String remoteUrl,
            @Value("${game.marketplace.remote-cache-seconds:300}") long cacheSeconds,
            @Value("${game.marketplace.require-checksums:false}") boolean requireChecksums,
            @Value("${game.marketplace.remote-hmac-secret:}") String hmacSecret,
            PackUploadService uploads) {
        this(Paths.get(contentPacksDir), remoteUrl, cacheSeconds, requireChecksums, hmacSecret, uploads,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build());
    }

    /** Visible for tests. */
    public MarketplaceService(
            Path root,
            String remoteUrl,
            long cacheSeconds,
            PackUploadService uploads) {
        this(root, remoteUrl, cacheSeconds, false, "", uploads,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build());
    }

    /** Visible for tests. */
    public MarketplaceService(
            Path root,
            String remoteUrl,
            long cacheSeconds,
            boolean requireChecksums,
            String hmacSecret,
            PackUploadService uploads) {
        this(root, remoteUrl, cacheSeconds, requireChecksums, hmacSecret, uploads,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build());
    }

    MarketplaceService(
            Path root,
            String remoteUrl,
            long cacheSeconds,
            boolean requireChecksums,
            String hmacSecret,
            PackUploadService uploads,
            HttpClient http) {
        this.root = root.toAbsolutePath().normalize();
        this.remoteUrl = remoteUrl == null ? "" : remoteUrl.trim();
        this.cacheTtlMs = Math.max(0L, cacheSeconds) * 1000L;
        this.requireChecksums = requireChecksums;
        this.hmacSecret = hmacSecret == null ? "" : hmacSecret.trim();
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
        String expected = MarketplaceIntegrity.normalizeSha256(listing.sha256());
        if (requireChecksums && expected == null) {
            return InstallResult.fail("Checksum required for remote pack: " + listing.id());
        }
        try {
            byte[] zip = downloadBytes(listing.downloadUrl());
            if (expected != null) {
                if (!MarketplaceIntegrity.sha256Matches(zip, expected)) {
                    return InstallResult.fail(
                            "SHA-256 mismatch for " + listing.id()
                                    + " (expected " + expected
                                    + ", got " + MarketplaceIntegrity.sha256Hex(zip) + ")");
                }
            }
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
            remoteCache.set(new CachedRemote(now - Math.max(0, cacheTtlMs - 30_000L), snap));
            return snap;
        }
    }

    private List<MarketplaceListing> fetchRemoteIndex(String url) throws Exception {
        DownloadedIndex downloaded = downloadIndex(url);
        byte[] raw = downloaded.body();

        if (!hmacSecret.isBlank()) {
            String provided = downloaded.signatureHeader();
            byte[] signedPayload = raw;
            if (provided == null || provided.isBlank()) {
                JsonNode probe = MAPPER.readTree(raw);
                provided = text(probe, "signature");
                signedPayload = stripJsonSignatureField(raw);
            }
            if (!MarketplaceIntegrity.hmacMatches(signedPayload, hmacSecret, provided)) {
                throw new IllegalStateException("Remote index HMAC verification failed");
            }
        }

        JsonNode root = MAPPER.readTree(raw);
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
            String sha = MarketplaceIntegrity.normalizeSha256(text(n, "sha256"));
            if (requireChecksums && sha == null) {
                System.err.println("[marketplace] skip remote pack without sha256: " + id);
                continue;
            }
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
                    downloadUrl,
                    sha));
        }
        return out;
    }

    private DownloadedIndex downloadIndex(String url) throws Exception {
        if (url.startsWith("file:")) {
            return new DownloadedIndex(Files.readAllBytes(Path.of(URI.create(url))), null);
        }
        if (url.startsWith("/") || url.startsWith("./") || (!url.contains("://") && Files.exists(Paths.get(url)))) {
            return new DownloadedIndex(Files.readAllBytes(Paths.get(url).toAbsolutePath().normalize()), null);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Path p = Paths.get(url);
            if (Files.isRegularFile(p)) {
                return new DownloadedIndex(Files.readAllBytes(p), null);
            }
            throw new IllegalArgumentException("Unsupported index URL: " + url);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .header("Accept", "application/json, */*")
                .build();
        HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("HTTP " + res.statusCode() + " for " + url);
        }
        String sig = res.headers().firstValue(SIGNATURE_HEADER).orElse(null);
        return new DownloadedIndex(res.body(), sig);
    }

    private byte[] downloadBytes(String url) throws Exception {
        if (url.startsWith("file:")) {
            return Files.readAllBytes(Path.of(URI.create(url)));
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
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

    /**
     * Remove top-level {@code signature} field for HMAC verification of embedded signatures.
     * Uses Jackson tree rewrite so key order of remaining fields is preserved as Map order
     * from the parser (good enough for our signing script which signs the stripped form).
     */
    static byte[] stripJsonSignatureField(byte[] body) throws IOException {
        JsonNode root = MAPPER.readTree(body);
        if (!(root instanceof com.fasterxml.jackson.databind.node.ObjectNode obj)) {
            return body;
        }
        obj.remove("signature");
        return MAPPER.writeValueAsBytes(obj);
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

    private record DownloadedIndex(byte[] body, String signatureHeader) {}

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
