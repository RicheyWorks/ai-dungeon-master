package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.Item;
import com.xai.dungeonmaster.util.ResourceLoader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifier logic tests that need no compilation: they feed the bytecode of real,
 * already-loaded classes to {@link SandboxVerifier}. ResourceLoader genuinely
 * uses java.nio.file, so it must trip the denylist; Item touches none of the
 * blocked APIs, so it must pass.
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
        byte[] bytes = bytesOf(ResourceLoader.class); // uses java.nio.file.*
        String violation = SandboxVerifier.firstViolation(bytes, SandboxPolicy.defaults());
        assertNotNull(violation, "ResourceLoader references java.nio.file and should be flagged");
        assertTrue(violation.startsWith("java/nio/file"), "unexpected violation: " + violation);
    }

    @Test
    void passesCleanClass() throws Exception {
        byte[] bytes = bytesOf(Item.class); // strings, enums, jackson annotations — nothing blocked
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
}
