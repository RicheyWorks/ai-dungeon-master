package com.xai.dungeonmaster.plugin;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.xai.dungeonmaster.util.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
 *   3. Verify the payload signature (see below) under the active
 *      {@link SignaturePolicy}. Reject on mismatch before any code runs.
 *   4. Create a child URLClassLoader scoped to the JAR.
 *   5. Reflectively load each entry class via its no-arg constructor.
 *   6. Hand the instantiated Plugin to the right registry.
 *
 * Signature verification:
 *   - {@code signature} in plugin.yaml is the expected SHA-256 (lowercase hex)
 *     of the plugin's payload: every JAR entry EXCEPT plugin.yaml itself,
 *     hashed in a deterministic name-sorted order (each entry's name and bytes
 *     mixed in). See {@link #computePayloadHash}.
 *   - The check runs BEFORE the child classloader is created, so a tampered
 *     JAR never gets its classes loaded or instantiated. This closes the old
 *     trust-on-load gap where the signature was parsed but never checked.
 *   - {@link SignaturePolicy} controls strictness. The loader still does not
 *     install a SecurityManager; sandboxing of loaded code is a later phase.
 *
 * Returns a {@link LoadReport} so callers can show users which plugins
 * loaded, which were skipped, which were rejected for a bad signature, and
 * which failed.
 */
public final class PluginLoader {

    /**
     * Engine version this loader speaks for. Plugins with a higher
     * minEngineVersion are rejected.
     */
    public static final String ENGINE_VERSION = "1.0.0";

    /** Manifest entry name; excluded from the payload hash so it can carry the signature. */
    static final String MANIFEST_ENTRY = "plugin.yaml";

    private static final YAMLMapper YAML = ResourceLoader.getYamlMapper();

    private PluginLoader() {}

    /**
     * How strictly the loader treats the {@code signature} field in plugin.yaml.
     */
    public enum SignaturePolicy {
        /** Verify when a signature is present (reject on mismatch); allow unsigned plugins with a warning. */
        LENIENT,
        /** Require a valid signature on every plugin; reject unsigned or mismatched. */
        REQUIRED,
        /** Skip signature verification entirely (legacy trust-on-load). */
        DISABLED
    }

    /** Result of a load pass. */
    public static final class LoadReport {
        public final List<String> loaded = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();
        /** Plugins refused because signature verification failed (a security stop, not an error). */
        public final List<String> rejected = new ArrayList<>();

        @Override
        public String toString() {
            return "PluginLoader.LoadReport[loaded=" + loaded.size()
                    + ", skipped=" + skipped.size()
                    + ", rejected=" + rejected.size()
                    + ", failed=" + failed.size() + "]";
        }
    }

    /**
     * Scan {@code pluginsDir} for *.jar files and load each one under the
     * default {@link SignaturePolicy#LENIENT} policy (unsigned plugins load
     * with a warning; signed plugins must verify).
     */
    public static LoadReport loadAll(Path pluginsDir) {
        return loadAll(pluginsDir, SignaturePolicy.LENIENT);
    }

    /**
     * Scan {@code pluginsDir} for *.jar files and load each one under the given
     * signature {@code policy}. Idempotent — re-running re-registers the same
     * plugins; registries are last-write-wins so this is safe.
     */
    public static LoadReport loadAll(Path pluginsDir, SignaturePolicy policy) {
        LoadReport report = new LoadReport();
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
            return report;
        }
        SignaturePolicy effective = (policy != null) ? policy : SignaturePolicy.LENIENT;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                try {
                    loadOneJar(jar, report, effective);
                } catch (Exception e) {
                    report.failed.add(jar.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            report.failed.add("scan-error: " + e.getMessage());
        }
        return report;
    }

    private static void loadOneJar(Path jarPath, LoadReport report, SignaturePolicy policy) throws Exception {
        PluginManifest manifest;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(MANIFEST_ENTRY);
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

        // Security gate: verify the payload hash against the declared signature
        // BEFORE any plugin code is loaded or instantiated.
        String sigError = verifySignature(jarPath, manifest, policy);
        if (sigError != null) {
            report.rejected.add(jarPath.getFileName() + ": " + sigError);
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

    /**
     * Verify the plugin's payload against the declared signature. Returns null
     * when the plugin is acceptable under {@code policy}, otherwise a
     * human-readable rejection reason.
     */
    private static String verifySignature(Path jarPath, PluginManifest manifest, SignaturePolicy policy)
            throws IOException {
        if (policy == SignaturePolicy.DISABLED) {
            return null;
        }
        String declared = (manifest.signature == null) ? "" : manifest.signature.trim();
        if (declared.isEmpty()) {
            if (policy == SignaturePolicy.REQUIRED) {
                return "unsigned plugin rejected (signature required)";
            }
            System.err.println("WARN: plugin '" + manifest.id
                    + "' is unsigned; loading under LENIENT policy.");
            return null;
        }
        String actual = computePayloadHash(jarPath);
        if (!actual.equalsIgnoreCase(declared)) {
            return "signature mismatch (declared " + shorten(declared)
                    + ", computed " + shorten(actual) + ")";
        }
        return null;
    }

    /**
     * SHA-256 (lowercase hex) over every JAR entry except {@link #MANIFEST_ENTRY},
     * hashed in a deterministic name-sorted order with each entry's name and
     * bytes mixed in. Package-private so unit tests can compute the expected
     * value for a freshly-built JAR.
     */
    static String computePayloadHash(Path jarPath) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Sort by entry name so the digest is independent of JAR ordering.
            TreeMap<String, JarEntry> sorted = new TreeMap<>();
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.isDirectory()) continue;
                if (MANIFEST_ENTRY.equals(je.getName())) continue;
                sorted.put(je.getName(), je);
            }
            byte[] buf = new byte[8192];
            for (Map.Entry<String, JarEntry> en : sorted.entrySet()) {
                md.update(en.getKey().getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0);
                try (InputStream is = jar.getInputStream(en.getValue())) {
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        md.update(buf, 0, n);
                    }
                }
            }
        }
        return toHex(md.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String shorten(String hex) {
        if (hex == null) return "null";
        return hex.length() <= 12 ? hex : hex.substring(0, 12) + "...";
    }

    /** Returns true if the instance was a known Plugin type and was registered. */
    private static boolean registerInstance(Object instance) {
        if (instance instanceof SpellEffect se)           { SpellEffectRegistry.register(se);    return true; }
        if (instance instanceof ItemEffect ie)            { ItemEffectRegistry.register(ie);     return true; }
        if (instance instanceof ContentPack cp)           { ContentRegistry.register(cp);        return true; }
        if (instance instanceof EncounterTable et)        { EncounterTableRegistry.register(et); return true; }
        if (instance instanceof LootTable lt)             { LootTableRegistry.register(lt);      return true; }
        if (instance instanceof QuestScript qs)           { QuestScriptRegistry.register(qs);    return true; }
        if (instance instanceof StorefrontIntegration si) { StorefrontRegistry.register(si);     return true; }
        if (instance instanceof LLMProvider lp)           { LLMProviderRegistry.register(lp);    return true; }
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
}
