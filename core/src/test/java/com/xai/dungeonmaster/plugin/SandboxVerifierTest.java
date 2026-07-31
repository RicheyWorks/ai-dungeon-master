package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Item;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifier logic tests that need no compilation: they feed the bytecode of real,
 * already-loaded classes to {@link SandboxVerifier}. ResourceLoader genuinely
 * uses blocked APIs (filesystem / ClassLoader), so it must trip the denylist;
 * Item touches none of the blocked APIs, so it must pass.
 */
class SandboxVerifierTest {

    private static byte[] bytesOf(Class<?> c) throws Exception {
        String path = "/" + c.getName().replace('.', '/') + ".class";
        try (InputStream is = c.getResourceAsStream(path)) {
            assertNotNull(is, "class resource not found: " + path);
            return is.readAllBytes();
        }
    }

    @Test
    void flagsClassThatUsesFilesystemApi() throws Exception {
        byte[] bytes = bytesOf(ResourceLoader.class);
        String violation = SandboxVerifier.firstViolation(bytes, SandboxPolicy.defaults());
        assertNotNull(violation, "ResourceLoader references blocked APIs and should be flagged");
        assertTrue(
                violation.startsWith("java/nio/file")
                        || violation.startsWith("java/io/File")
                        || violation.startsWith("java/lang/ClassLoader"),
                "unexpected violation: " + violation);
    }

    @Test
    void passesCleanClass() throws Exception {
        byte[] bytes = bytesOf(Item.class);
        assertNull(SandboxVerifier.firstViolation(bytes, SandboxPolicy.defaults()),
                "Item uses no blocked API and should pass");
    }

    @Test
    void disabledPolicyNeverFlags() throws Exception {
        byte[] bytes = bytesOf(ResourceLoader.class);
        assertNull(SandboxVerifier.firstViolation(bytes, SandboxPolicy.disabled()),
                "a disabled policy must not scan");
    }

    @Test
    void classLoaderIsDenied() {
        assertTrue(SandboxPolicy.defaults().isDenied("java/lang/ClassLoader"));
        assertTrue(SandboxPolicy.defaults().isDenied("java/lang/ClassLoader$1"));
        assertTrue(SandboxPolicy.defaults().isDenied("java/net/URLClassLoader"));
    }

    @Test
    void baseInternalNameStripsArraysAndWrappers() {
        assertEquals("java/lang/Runtime", SandboxVerifier.baseInternalName("java/lang/Runtime"));
        assertEquals("java/lang/Runtime", SandboxVerifier.baseInternalName("[Ljava/lang/Runtime;"));
        assertEquals("java/lang/Runtime", SandboxVerifier.baseInternalName("[[Ljava/lang/Runtime;"));
        assertNull(SandboxVerifier.baseInternalName("[I"), "primitive arrays have no class name");
    }

    @Test
    void garbageBytesAreTreatedAsViolation() {
        String v = SandboxVerifier.firstViolation(new byte[] { 1, 2, 3, 4 }, SandboxPolicy.defaults());
        assertNotNull(v, "non-class bytes should be rejected, not silently passed");
    }

    @Test
    void nativeMethodIsRejected() throws Exception {
        javax.tools.JavaCompiler jc = javax.tools.ToolProvider.getSystemJavaCompiler();
        org.junit.jupiter.api.Assumptions.assumeTrue(jc != null, "no system JavaCompiler");

        java.nio.file.Path work = java.nio.file.Files.createTempDirectory("native-scan");
        try {
            java.nio.file.Path src = work.resolve("NativeEscape.java");
            java.nio.file.Files.writeString(src,
                    "public class NativeEscape { public native void pwn(); }");
            java.nio.file.Path out = work.resolve("out");
            java.nio.file.Files.createDirectories(out);
            int rc = jc.run(null, null, null, "-d", out.toString(), src.toString());
            assertEquals(0, rc, "compile native method class");
            byte[] bytes = java.nio.file.Files.readAllBytes(out.resolve("NativeEscape.class"));
            String v = SandboxVerifier.firstViolation(bytes, SandboxPolicy.defaults());
            assertNotNull(v, "native method must be flagged");
            assertTrue(v.startsWith("native method"), "unexpected: " + v);
        } finally {
            try (var walk = java.nio.file.Files.walk(work)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) {} });
            }
        }
    }
}
