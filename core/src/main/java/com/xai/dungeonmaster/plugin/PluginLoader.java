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
 *   3. Verify the payload signature under the active {@link SignaturePolicy}.
 *   4. Load each entry class through a {@link SandboxedClassLoader} that, under
 *      the active {@link SandboxPolicy}, scans plugin bytecode and refuses
 *      classes referencing blocked APIs (process execution, reflection, raw
 *      networking, filesystem, JDK internals).
 *   5. Reflectively instantiate each entry class and hand it to the right registry.
 *
 * Two security gates run BEFORE any plugin code executes: signature
 * verification (integrity) and the sandbox scan (capability). A JAR that fails
 * either is reported under {@code rejected}, never {@code failed}.
 *
 * Returns a {@link LoadReport} so callers can show users which plugins loaded,
 * were skipped, were rejected for security, and which failed.
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
        /** Plugins refused for security (bad signature or sandbox violation) — not an error. */
        public final List<String> rejected = new ArrayList<>();

        @Override
        public String toString() {
            return "PluginLoader.LoadReport[loaded=" + loaded.size()
                    + ", skipped=" + skipped.size()
                    + ", rejected=" + rejected.size()
                    + ", failed=" + failed.size() + "]";
        }
    }

    /** Scan and load with the default LENIENT signature policy and the default sandbox. */
    public static LoadReport loadAll(Path pluginsDir) {
        return loadAll(pluginsDir, SignaturePolicy.LENIENT, SandboxPolicy.defaults());
    }

    /** Scan and load under {@code signaturePolicy}, with the default sandbox. */
    public static LoadReport loadAll(Path pluginsDir, SignaturePolicy signaturePolicy) {
        return loadAll(pluginsDir, signaturePolicy, SandboxPolicy.defaults());
    }

    /**
     * Scan {@code pluginsDir} for *.jar files and load each under the given
     * signature and sandbox policies. Idempotent — registries are last-write-wins.
     */
    public static LoadReport loadAll(Path pluginsDir, SignaturePolicy signaturePolicy, SandboxPolicy sandboxPolicy) {
        LoadReport report = new LoadReport();
        if (pluginsDir == null || !Files.isDirectory(pluginsDir)) {
            return report;
        }
        SignaturePolicy sig = (signaturePolicy != null) ? signaturePolicy : SignaturePolicy.LENIENT;
        SandboxPolicy sandbox = (sandboxPolicy != null) ? sandboxPolicy : SandboxPolicy.defaults();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : stream) {
                try {
                    loadOneJar(jar, report, sig, sandbox);
                } catch (Exception e) {
                    report.failed.add(jar.getFileName() + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            report.failed.add("scan-error: " + e.getMessage());
        }
        return report;
    }

    private static void loadOneJar(Path jarPath, LoadReport report,
                                   SignaturePolicy signaturePolicy, SandboxPolicy sandboxPolicy) throws Exception {
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

        // Security gate 1: verify the payload hash against the declared signature.
        String sigError = verifySignature(jarPath, manifest, signaturePolicy);
        if (sigError != null) {
            report.rejected.add(jarPath.getFileName() + ": " + sigError);
            return;
        }

        // Security gate 2: the sandboxing classloader scans plugin bytecode as it
        // defines each class, throwing SecurityException on a blocked API.
        URL[] urls = { jarPath.toUri().toURL() };
        ClassLoader parent = PluginLoader.class.getClassLoader();
        // The loader is intentionally NOT closed: plugin classes may be resolved
        // lazily during gameplay, so it must live for the process lifetime.
        @SuppressWarnings("resource")
        URLClassLoader loader = (sandboxPolicy != null && sandboxPolicy.isEnabled())
                ? new SandboxedClassLoader(urls, parent, sandboxPolicy)
                : new URLClassLoader(urls, parent);

        int registered = 0;
        for (String className : manifest.entryClassesOrEmpty()) {
            try {
                Class<?> cls = Class.forName(className, true, loader);
                Object instance = cls.getDeclaredConstructor().newInstance();
                if (registerInstance(instance)) registered++;
                else report.skipped.add(jarPath.getFileName() + ": "
                        + className + " is not a recognised Plugin type");
            } catch (SecurityException se) {
                report.rejected.add(jarPath.getFileName() + ": " + className
                        + " blocked by sandbox — " + se.getMessage());
            } catch (ReflectiveOperationException | LinkageError roe) {
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
     * when acceptable under {@code policy}, otherwise a rejection reason.
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
     * bytes mixed in. Package-private so unit tests can compute the expected value.
     */
    static String computePayloadHash(Path jarPath) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
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
