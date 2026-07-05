package com.xai.dungeonmaster.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies plugin JAR signature checking in {@link PluginLoader}. Each test
 * builds a real JAR on disk (plugin.yaml + a payload entry) and exercises a
 * signature policy. The entry class is the bundled {@link
 * com.xai.dungeonmaster.plugin.builtin.NoOpStorefront} so a verified JAR
 * actually registers and shows up in {@code report.loaded}.
 */
class PluginSignatureTest {

    private static final String ENTRY_CLASS = "com.xai.dungeonmaster.plugin.builtin.NoOpStorefront";
    private static final String PAYLOAD_NAME = "payload/data.bin";

    @AfterEach
    void reset() {
        StorefrontRegistry.clearForTests(); // drop anything a loaded JAR registered
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String manifest(String signatureOrNull) {
        StringBuilder sb = new StringBuilder();
        sb.append("id: \"sig-test\"\n");
        sb.append("displayName: \"Signature Test Plugin\"\n");
        sb.append("version: \"1.0.0\"\n");
        sb.append("minEngineVersion: \"1.0.0\"\n");
        sb.append("entryClasses:\n");
        sb.append("  - \"").append(ENTRY_CLASS).append("\"\n");
        if (signatureOrNull != null) {
            sb.append("signature: \"").append(signatureOrNull).append("\"\n");
        }
        return sb.toString();
    }

    private static Path buildJar(Path dir, String fileName, String manifestYaml, byte[] payload) throws IOException {
        Files.createDirectories(dir);
        Path jar = dir.resolve(fileName);
        try (OutputStream os = Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(os)) {
            jos.putNextEntry(new JarEntry(PluginLoader.MANIFEST_ENTRY));
            jos.write(manifestYaml.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
            jos.putNextEntry(new JarEntry(PAYLOAD_NAME));
            jos.write(payload);
            jos.closeEntry();
        }
        return jar;
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void validSignatureLoads(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("valid");
        byte[] payload = "the plugin payload bytes".getBytes(StandardCharsets.UTF_8);
        // plugin.yaml is excluded from the hash, so we can hash an unsigned build
        // and then re-stamp the manifest with that value.
        Path scratch = buildJar(dir, "plugin.jar", manifest(null), payload);
        String hash = PluginLoader.computePayloadHash(scratch);
        buildJar(dir, "plugin.jar", manifest(hash), payload); // overwrite with signed manifest

        PluginLoader.LoadReport report = PluginLoader.loadAll(dir, PluginLoader.SignaturePolicy.LENIENT);
        assertEquals(1, report.loaded.size(), "correctly-signed plugin should load");
        assertTrue(report.rejected.isEmpty(), "no rejections expected: " + report.rejected);
    }

    @Test
    void tamperedPayloadIsRejected(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("tampered");
        // Hash a payload, then ship a DIFFERENT payload under that signature.
        Path original = buildJar(dir, "orig.jar", manifest(null), "ORIGINAL".getBytes(StandardCharsets.UTF_8));
        String hashOfOriginal = PluginLoader.computePayloadHash(original);
        Files.delete(original);
        buildJar(dir, "plugin.jar", manifest(hashOfOriginal), "TAMPERED".getBytes(StandardCharsets.UTF_8));

        PluginLoader.LoadReport report = PluginLoader.loadAll(dir, PluginLoader.SignaturePolicy.LENIENT);
        assertTrue(report.loaded.isEmpty(), "tampered plugin must not load");
        assertEquals(1, report.rejected.size(), "tampered plugin must be rejected");
        assertTrue(report.rejected.get(0).contains("signature mismatch"), report.rejected.get(0));
    }

    @Test
    void requiredPolicyRejectsUnsignedPlugin(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("unsigned-required");
        buildJar(dir, "plugin.jar", manifest(null), "code".getBytes(StandardCharsets.UTF_8));

        PluginLoader.LoadReport report = PluginLoader.loadAll(dir, PluginLoader.SignaturePolicy.REQUIRED);
        assertTrue(report.loaded.isEmpty(), "unsigned plugin must not load under REQUIRED");
        assertEquals(1, report.rejected.size());
        assertTrue(report.rejected.get(0).contains("signature required"), report.rejected.get(0));
    }

    @Test
    void lenientPolicyAllowsUnsignedPlugin(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("unsigned-lenient");
        buildJar(dir, "plugin.jar", manifest(null), "code".getBytes(StandardCharsets.UTF_8));

        PluginLoader.LoadReport report = PluginLoader.loadAll(dir, PluginLoader.SignaturePolicy.LENIENT);
        assertEquals(1, report.loaded.size(), "unsigned plugin should load under LENIENT");
        assertTrue(report.rejected.isEmpty());
    }

    @Test
    void disabledPolicySkipsVerification(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("bad-sig-disabled");
        buildJar(dir, "plugin.jar", manifest("deadbeefdeadbeef"), "code".getBytes(StandardCharsets.UTF_8));

        PluginLoader.LoadReport report = PluginLoader.loadAll(dir, PluginLoader.SignaturePolicy.DISABLED);
        assertEquals(1, report.loaded.size(), "DISABLED policy should skip the check and load");
        assertTrue(report.rejected.isEmpty());
    }
}
