package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.dto.MarketplaceListing;
import com.xai.dungeonmaster.dto.MarketplacePayload;
import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Local content-pack marketplace: discovers packs under
 * {@code game.content-packs.dir} (default {@code content-packs/}) and can
 * install a pack that is present on disk but not yet registered.
 */
@Service
public class MarketplaceService {

    private final Path root;

    public MarketplaceService(
            @Value("${game.content-packs.dir:content-packs}") String contentPacksDir) {
        this.root = Paths.get(contentPacksDir).toAbsolutePath().normalize();
    }

    /** Visible for tests. */
    public MarketplaceService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public MarketplacePayload list(String query) {
        List<MarketplaceListing> packs = scan();
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase(Locale.ROOT);
            packs = packs.stream()
                    .filter(p -> contains(p.id(), q)
                            || contains(p.displayName(), q)
                            || contains(p.description(), q))
                    .toList();
        }
        int installed = (int) packs.stream().filter(MarketplaceListing::installed).count();
        return new MarketplacePayload(root.toString(), packs.size(), installed, packs);
    }

    public Optional<MarketplaceListing> get(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return scan().stream().filter(p -> p.id().equalsIgnoreCase(id.trim())).findFirst();
    }

    /**
     * Install a marketplace pack into the live {@link ContentRegistry}.
     * Idempotent if already installed.
     */
    public InstallResult install(String id) {
        if (id == null || id.isBlank()) {
            return InstallResult.fail("Missing pack id");
        }
        String want = id.trim();
        MarketplaceListing listing = get(want).orElse(null);
        if (listing == null) {
            return InstallResult.fail("Unknown marketplace pack: " + want);
        }
        if (ContentRegistry.isKnown(listing.id())) {
            return InstallResult.already(listing.id());
        }
        Path dir = Paths.get(listing.sourcePath());
        ContentPack pack = ResourceLoader.loadAndRegisterPack(dir);
        if (pack == null) {
            return InstallResult.fail("Failed to load pack at " + dir);
        }
        ContentRegistry.setEnabled(pack.id(), true);
        return InstallResult.installed(pack.id());
    }

    private List<MarketplaceListing> scan() {
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
                out.add(new MarketplaceListing(
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
            System.err.println("[marketplace] scan failed: " + e.getMessage());
        }
        out.sort(Comparator.comparing(MarketplaceListing::id, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
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
