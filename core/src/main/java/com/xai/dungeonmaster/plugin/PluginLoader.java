package com.xai.dungeonmaster.plugin;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.xai.dungeonmaster.util.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Discovers and loads code-bearing plugins from JAR files under a
 * configurable {@code plugins/} directory.
 *
 * Workflow per JAR:
 *   1. Open the JAR and look for plugin.yaml at its root.
 *   2. Parse the manifest. Reject if id/version/entryClasses are missing
 *      or if minEngineVersion exceeds {@link #ENGINE_VERSION}.
 *   3. Create a child URLClassLoader scoped to the JAR.
 *   4. Reflectively load each entry class via its no-arg constructor.
 *   5. Hand the instantiated Plugin to the right registry
 *      (SpellEffectRegistry, ItemEffectRegistry, ContentRegistry, ...).
 *
 * Safety:
 *   - The loader does NOT install a SecurityManager. For Phase 1 the
 *     focus is the wiring; sandboxing comes later (Phase 5+).
 *   - {@code signature} in plugin.yaml is parsed but not yet verified.
 *     A future step will hash the JAR contents and compare against a
 *     server-issued signature before instantiation.
 *
 * Returns a {@link LoadReport} so callers can show users which plugins
 * loaded, which were skipped, and why.
 */
public final class PluginLoader {

    /**
     * Engine version this loader speaks for. Plugins with a higher
     * minEngineVersion are rejected.
     */
    public static final String ENGINE_VERSION = "1.0.0";

    private static final YAMLMapper YAML = ResourceLoader.getYamlMapper();

    private PluginLoader() {}

    /** Result of a load pass. */
    public static final class LoadReport {
        public final List<String> loaded = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();

        @Override
        public String toString() {
            return "PluginLoader.LoadReport[loaded=" + loaded.size()
                    + ", skipped=" + skipped.size()
                    + ", failed=" + failed.size() + "]";
        }
    }

    /**
     * Scan {@code pluginsDir} for *.jar files and load each one.
     * Idempotent — re-running will re-register the same plugins;
     * registries are last-write-wins so this is safe.
     */
    public static LoadReport loadAll(Path pluginsDir) {
        LoadReport report = new LoadReport();
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
            return report;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                try {
                    loadOneJar(jar, report);
                } catch (Exception e) {
                    report.failed.add(jar.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            report.failed.add("scan-error: " + e.getMessage());
        }
        return report;
    }

    private static void loadOneJar(Path jarPath, LoadReport report) throws Exception {
        PluginManifest manifest;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry("plugin.yaml");
            if (entry == null) {
                report.skipped.add(jarPath.getFileName() + ": no plugin.yaml at JAR root");
                return;
            }
            try (InputStream is = jar.getInputStream(entry)) {
                manifest = YAML.readValue(is, PluginManifest.class);
            }
        }

        if (manifest == null || manifest.id == null || manifest.id.isBlank()) {
            report.skipped.add(jarPath.getFileName() + ": invalid manifest (no id)");
            return;
        }
        if (!isCompatible(manifest.minEngineVersion)) {
            report.skipped.add(jarPath.getFileName()
                    + ": requires engine " + manifest.minEngineVersion
                    + " (have " + ENGINE_VERSION + ")");
            return;
        }
        if (manifest.entryClassesOrEmpty().isEmpty()) {
            report.skipped.add(jarPath.getFileName() + ": no entryClasses declared");
            return;
        }

        URL[] urls = { jarPath.toUri().toURL() };
        URLClassLoader loader = new URLClassLoader(urls, PluginLoader.class.getClassLoader());

        int registered = 0;
        for (String className : manifest.entryClassesOrEmpty()) {
            try {
                Class<?> cls = Class.forName(className, true, loader);
                Object instance = cls.getDeclaredConstructor().newInstance();
                if (registerInstance(instance)) registered++;
                else report.skipped.add(jarPath.getFileName() + ": "
                        + className + " is not a recognised Plugin type");
            } catch (ReflectiveOperationException roe) {
                report.failed.add(jarPath.getFileName() + ": " + className
                        + " — " + roe.getClass().getSimpleName() + ": " + roe.getMessage());
            }
        }

        if (registered > 0) {
            report.loaded.add(jarPath.getFileName() + " (" + manifest.id
                    + " v" + manifest.version + ", " + registered + " entries)");
        }
    }

    /** Returns true if the instance was a known Plugin type and was registered. */
    private static boolean registerInstance(Object instance) {
        if (instance instanceof SpellEffect se) { SpellEffectRegistry.register(se); return true; }
        if (instance instanceof ItemEffect ie)  { ItemEffectRegistry.register(ie);  return true; }
        if (instance instanceof ContentPack cp) { ContentRegistry.register(cp);     return true; }
        // Future: LLMProvider, StorefrontIntegration, EncounterTable, LootTable, QuestScript.
        // The host service layer will provide the registries for those — they have
        // server-side wiring (HTTP clients, native libs) that doesn't belong in core.
        return false;
    }

    /**
     * Simple x.y.z comparison. Returns true if {@code required} is null,
     * blank, or <= ENGINE_VERSION.
     */
    private static boolean isCompatible(String required) {
        if (required == null || required.isBlank()) return true;
        int[] r = parseVersion(required);
        int[] e = parseVersion(ENGINE_VERSION);
        for (int i = 0; i < 3; i++) {
            if (r[i] < e[i]) return true;
            if (r[i] > e[i]) return false;
        }
        return true;
    }

    private static int[] parseVersion(String v) {
        int[] out = { 0, 0, 0 };
        String[] parts = v.trim().split("\\.");
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try { out[i] = Integer.parseInt(parts[i].replaceAll("\\D.*$", "")); }
            catch (NumberFormatException ignored) {}
        }
        return out;
    }

    /** Enumerate jar entries (helper kept for future signing checks). */
    @SuppressWarnings("unused")
    private static List<String> listEntries(JarFile jar) {
        List<String> names = new ArrayList<>();
        Enumeration<JarEntry> e = jar.entries();
        while (e.hasMoreElements()) names.add(e.nextElement().getName());
        return names;
    }
}
