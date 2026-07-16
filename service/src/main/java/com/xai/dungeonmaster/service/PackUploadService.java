package com.xai.dungeonmaster.service;

import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Runtime content-pack installation (roadmap Phase 5: pack upload).
 *
 * Accepts a zipped pack (pack.yaml + items/, monsters/, strings/, quests/,
 * campaigns/, npcs/, factions/), validates it defensively, extracts it under
 * {@code game.content.packs.dir}/&lt;packId&gt;/, and registers it through the
 * same {@link ResourceLoader} path the startup scan uses — so an uploaded
 * pack behaves identically to one shipped on disk, including its quests and
 * campaigns.
 *
 * Validation, in order:
 * - zip-slip guard: entries may not be absolute or contain ".." segments;
 * - bounded: at most {@link #MAX_ENTRIES} entries and
 *   {@link #MAX_TOTAL_BYTES} uncompressed bytes;
 * - must contain a pack.yaml (at the root, or under a single top directory,
 *   which is stripped — both `zip pack/ -r` and flat zips work);
 * - manifest id must be non-blank and filesystem-safe (lowercase kebab);
 * - an already-registered id is a conflict unless {@code replace} is set.
 *
 * Packs are pure data — no code is loaded by this path. (Code-bearing mods go
 * through the signed + sandboxed {@code PluginLoader}, not pack upload.)
 */
@Service
public class PackUploadService {

    /** Maximum number of entries a pack zip may contain. */
    public static final int MAX_ENTRIES = 500;

    /** Maximum total uncompressed size of a pack zip (bytes). */
    public static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;

    private static final java.util.regex.Pattern SAFE_ID =
            java.util.regex.Pattern.compile("[a-z0-9][a-z0-9-_]{0,63}");

    private final Path packsDir;

    public PackUploadService(@Value("${game.content.packs.dir:content-packs}") String packsDir) {
        this.packsDir = Paths.get(packsDir);
    }

    /** Upload outcome: the registered pack and where it was installed. */
    public record InstalledPack(ContentPack pack, Path dir, boolean replaced) {}

    /** Thrown for every rejected upload; {@code conflict} maps to HTTP 409. */
    public static class PackUploadException extends Exception {
        private final boolean conflict;
        public PackUploadException(String message) { this(message, false); }
        public PackUploadException(String message, boolean conflict) {
            super(message);
            this.conflict = conflict;
        }
        public boolean isConflict() { return conflict; }
    }

    /**
     * Validate, extract, and register an uploaded pack zip.
     */
    public InstalledPack install(byte[] zipBytes, boolean replace) throws PackUploadException {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new PackUploadException("Empty upload.");
        }

        Map<String, byte[]> entries = readEntries(zipBytes);
        String rootPrefix = commonRootPrefix(entries);

        byte[] manifestBytes = entries.get(rootPrefix + "pack.yaml");
        if (manifestBytes == null) {
            throw new PackUploadException(
                    "No pack.yaml found (expected at the zip root or under a single top-level directory).");
        }

        String id = parsePackId(manifestBytes);
        boolean replaced = ContentRegistry.isKnown(id);
        if (replaced && !replace) {
            throw new PackUploadException(
                    "Content pack '" + id + "' is already installed. Re-upload with replace=true to overwrite.",
                    true);
        }

        Path target = packsDir.resolve(id).normalize();
        if (!target.startsWith(packsDir)) {
            throw new PackUploadException("Pack id resolves outside the packs directory: " + id);
        }

        writeEntries(entries, rootPrefix, target);

        ContentPack pack = ResourceLoader.loadAndRegisterPack(target);
        if (pack == null) {
            throw new PackUploadException("Extracted pack failed to load — see server logs.");
        }
        return new InstalledPack(pack, target, replaced);
    }

    // ─── Zip reading + validation ───────────────────────────────────────────

    private static Map<String, byte[]> readEntries(byte[] zipBytes) throws PackUploadException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.isBlank()) continue;
                if (name.startsWith("/") || name.contains("..")) {
                    throw new PackUploadException("Rejected unsafe zip entry path: " + name);
                }
                if (entry.isDirectory()) continue;
                if (entries.size() >= MAX_ENTRIES) {
                    throw new PackUploadException("Pack zip exceeds " + MAX_ENTRIES + " entries.");
                }
                byte[] data = zis.readAllBytes();
                total += data.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw new PackUploadException(
                            "Pack zip exceeds " + (MAX_TOTAL_BYTES / (1024 * 1024)) + " MB uncompressed.");
                }
                entries.put(name, data);
            }
        } catch (IOException e) {
            throw new PackUploadException("Not a readable zip archive: " + e.getMessage());
        }
        if (entries.isEmpty()) {
            throw new PackUploadException("Zip archive contains no files.");
        }
        return entries;
    }

    /** "dir/" when every entry lives under one top-level directory, else "". */
    private static String commonRootPrefix(Map<String, byte[]> entries) {
        String prefix = null;
        for (String name : entries.keySet()) {
            int slash = name.indexOf('/');
            String top = (slash < 0) ? "" : name.substring(0, slash + 1);
            if (prefix == null) prefix = top;
            if (top.isEmpty() || !top.equals(prefix)) return "";
        }
        return prefix != null ? prefix : "";
    }

    private String parsePackId(byte[] manifestBytes) throws PackUploadException {
        try {
            ResourceLoader.PackManifest meta = ResourceLoader.getYamlMapper()
                    .readValue(manifestBytes, ResourceLoader.PackManifest.class);
            String id = (meta != null && meta.id != null) ? meta.id.trim() : "";
            if (id.isEmpty()) {
                throw new PackUploadException("pack.yaml is missing the required 'id'.");
            }
            if (!SAFE_ID.matcher(id).matches()) {
                throw new PackUploadException(
                        "Pack id '" + id + "' is not filesystem-safe (want lowercase letters, digits, - or _).");
            }
            return id;
        } catch (PackUploadException e) {
            throw e;
        } catch (Exception e) {
            throw new PackUploadException("Invalid pack.yaml: " + e.getMessage());
        }
    }

    private static void writeEntries(Map<String, byte[]> entries, String rootPrefix, Path target)
            throws PackUploadException {
        try {
            Files.createDirectories(target);
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                String relative = e.getKey().substring(rootPrefix.length());
                if (relative.isBlank()) continue;
                Path out = target.resolve(relative).normalize();
                if (!out.startsWith(target)) {
                    throw new PackUploadException("Rejected unsafe zip entry path: " + e.getKey());
                }
                Files.createDirectories(out.getParent());
                Files.write(out, e.getValue());
            }
        } catch (IOException e) {
            throw new PackUploadException("Failed to write pack files: " + e.getMessage());
        }
    }
}
