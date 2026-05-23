package com.xai.dungeonmaster.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.xai.dungeonmaster.Enemy;
import com.xai.dungeonmaster.Item;
import com.xai.dungeonmaster.plugin.ContentPack;
import com.xai.dungeonmaster.plugin.ContentRegistry;
import com.xai.dungeonmaster.plugin.DefaultContentPack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * Resource loading utility.
 *
 * Two layers:
 *   1. Classpath loading (loadItems, loadMonsters, getLocalizedString) for
 *      the built-in content shipped with the core jar.
 *   2. Filesystem content-pack scanning (scanContentPacks) for runtime-
 *      installable themed packs like D&D-Classic, Sci-Fi, Horror, Cozy.
 *
 * The intended startup sequence is:
 *   ResourceLoader.registerAllContentPacks(Paths.get("content-packs"));
 *
 * After that, DungeonGenerator draws from ContentRegistry directly.
 */
public final class ResourceLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final YAMLMapper YAML = new YAMLMapper();
    private static final Properties CONFIG = new Properties();
    private static ResourceBundle strings;

    static {
        MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        YAML.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try (InputStream is = ResourceLoader.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (is != null) {
                CONFIG.load(is);
            } else {
                System.err.println("WARN: config.properties not found. Using defaults.");
            }
        } catch (Exception e) {
            System.err.println("WARN: Failed to load config.properties: " + e.getMessage());
        }

        try {
            strings = ResourceBundle.getBundle("strings", Locale.getDefault());
        } catch (MissingResourceException e) {
            System.err.println("WARN: Localization bundle not found. Using fallback keys.");
            strings = null;
        }
    }

    private ResourceLoader() {}

    // ─── Classpath helpers ──────────────────────────────────────────────────

    public static String getLocalizedString(String key) {
        if (key == null || key.isBlank()) return "";
        try {
            return strings != null ? strings.getString(key) : key;
        } catch (Exception e) {
            return key;
        }
    }

    public static String getConfig(String key, String defaultValue) {
        if (key == null || key.isBlank()) return defaultValue;
        return CONFIG.getProperty(key, defaultValue);
    }

    public static <T> T loadData(String fileName, TypeReference<T> typeReference) {
        if (fileName == null || fileName.isBlank() || typeReference == null) return null;
        try (InputStream is = ResourceLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                System.err.println("Resource not found: " + fileName);
                return null;
            }
            return MAPPER.readValue(is, typeReference);
        } catch (Exception e) {
            System.err.println("Failed to parse " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    public static Map<String, Item> loadItems() {
        Map<String, Item> items = loadData("items.json", new TypeReference<Map<String, Item>>() {});
        return items != null ? items : Collections.emptyMap();
    }

    public static Map<String, Enemy> loadMonsters() {
        Map<String, Enemy> monsters = loadData("monsters.json", new TypeReference<Map<String, Enemy>>() {});
        return monsters != null ? monsters : Collections.emptyMap();
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    public static YAMLMapper getYamlMapper() {
        return YAML;
    }

    // ─── Content pack scanning ──────────────────────────────────────────────

    /**
     * Scan a directory for content packs. Each immediate subdirectory is
     * treated as a candidate pack; it must contain a pack.yaml manifest
     * and may contain items/*.json, monsters/*.json, and strings/*.properties.
     *
     * The current locale's strings file is preferred (e.g., strings/en.properties
     * for an English locale). Falls back to strings/en.properties if the
     * locale-specific file is missing.
     *
     * Returns an empty list if the root doesn't exist or contains no valid
     * packs — never throws for normal "no packs installed" cases.
     */
    public static java.util.List<ContentPack> scanContentPacks(Path root) {
        java.util.List<ContentPack> result = new java.util.ArrayList<>();
        if (root == null || !Files.isDirectory(root)) return result;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;
                Path manifest = dir.resolve("pack.yaml");
                if (!Files.isRegularFile(manifest)) continue;
                try {
                    ContentPack pack = loadOnePack(dir, manifest);
                    if (pack != null) result.add(pack);
                } catch (Exception e) {
                    System.err.println("Failed to load content pack at " + dir + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to scan " + root + " for content packs: " + e.getMessage());
        }
        return result;
    }

    /**
     * Convenience: register the bundled pack + every external pack found
     * under {@code root}. Returns the number of external packs loaded
     * (the bundled pack is not counted).
     */
    public static int registerAllContentPacks(Path root) {
        ContentRegistry.register(new DefaultContentPack());
        java.util.List<ContentPack> found = scanContentPacks(root);
        for (ContentPack pack : found) {
            ContentRegistry.register(pack);
        }
        return found.size();
    }

    private static ContentPack loadOnePack(Path dir, Path manifest) throws IOException {
        PackManifest meta = YAML.readValue(manifest.toFile(), PackManifest.class);
        if (meta == null || meta.id == null || meta.id.isBlank()) {
            System.err.println("Invalid pack.yaml at " + manifest + " — missing 'id'");
            return null;
        }

        Map<String, Item> items = mergeJsonDir(dir.resolve("items"), Item.class);
        Map<String, Enemy> monsters = mergeJsonDir(dir.resolve("monsters"), Enemy.class);
        Map<String, String> strings = loadLocaleStrings(dir.resolve("strings"));

        return new FilesystemContentPack(meta, items, monsters, strings);
    }

    private static <T> Map<String, T> mergeJsonDir(Path dir, Class<T> elementType) {
        Map<String, T> merged = new HashMap<>();
        if (!Files.isDirectory(dir)) return merged;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                try (InputStream is = Files.newInputStream(file)) {
                    Map<String, T> parsed = MAPPER.readValue(is,
                            MAPPER.getTypeFactory().constructMapType(HashMap.class, String.class, elementType));
                    if (parsed != null) merged.putAll(parsed);
                } catch (Exception e) {
                    System.err.println("Failed to parse " + file + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to scan " + dir + ": " + e.getMessage());
        }
        return merged;
    }

    private static Map<String, String> loadLocaleStrings(Path dir) {
        Map<String, String> result = new HashMap<>();
        if (!Files.isDirectory(dir)) return result;

        String langTag = Locale.getDefault().getLanguage();
        Path preferred = dir.resolve(langTag + ".properties");
        Path fallback = dir.resolve("en.properties");
        Path pick = Files.isRegularFile(preferred) ? preferred
                : (Files.isRegularFile(fallback) ? fallback : null);
        if (pick == null) return result;

        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(pick)) {
            props.load(is);
        } catch (IOException e) {
            System.err.println("Failed to load " + pick + ": " + e.getMessage());
            return result;
        }
        for (String name : props.stringPropertyNames()) {
            result.put(name, props.getProperty(name));
        }
        return result;
    }

    // ─── Manifest + pack types ──────────────────────────────────────────────

    /** Mirrors pack.yaml. Public so Jackson can populate it via reflection. */
    public static final class PackManifest {
        public String id;
        public String displayName;
        public String version;
        public String minEngineVersion;
        public String description;
    }

    /** ContentPack backed by a pre-loaded set of maps. */
    private static final class FilesystemContentPack implements ContentPack {
        private final PackManifest meta;
        private final Map<String, Item> items;
        private final Map<String, Enemy> monsters;
        private final Map<String, String> strings;

        FilesystemContentPack(PackManifest meta,
                              Map<String, Item> items,
                              Map<String, Enemy> monsters,
                              Map<String, String> strings) {
            this.meta = meta;
            this.items = items;
            this.monsters = monsters;
            this.strings = strings;
        }

        @Override public String id() { return meta.id; }
        @Override public String displayName() { return meta.displayName != null ? meta.displayName : meta.id; }
        @Override public String version() { return meta.version != null ? meta.version : "0.0.0"; }
        @Override public String minEngineVersion() { return meta.minEngineVersion != null ? meta.minEngineVersion : "1.0.0"; }
        @Override public Map<String, Item> items() { return Collections.unmodifiableMap(items); }
        @Override public Map<String, Enemy> monsters() { return Collections.unmodifiableMap(monsters); }
        @Override public Map<String, String> strings() { return Collections.unmodifiableMap(strings); }
    }
}
