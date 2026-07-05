package com.xai.dungeonmaster.plugin;

import com.xai.dungeonmaster.plugin.PluginLoader.LoadReport;
import com.xai.dungeonmaster.plugin.PluginLoader.SignaturePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end sandbox test: compiles real plugin classes in-JVM, packs them into
 * JARs, and asserts {@link PluginLoader} loads the benign one but rejects the
 * one that references a blocked API (java.lang.Runtime) — and that disabling the
 * sandbox lets even the malicious one through.
 */
class PluginSandboxTest {

    private static final String GOOD_SRC =
            "package sandboxdemo;"
            + "import com.xai.dungeonmaster.plugin.ItemEffect;"
            + "import com.xai.dungeonmaster.Adventurer;"
            + "import com.xai.dungeonmaster.DungeonMasterEngine;"
            + "import com.xai.dungeonmaster.Item;"
            + "public class GoodEffect implements ItemEffect {"
            + "  public String id() { return \"SANDBOX_GOOD\"; }"
            + "  public String execute(DungeonMasterEngine engine, Adventurer user, Item item) {"
            + "    return \"You feel a little better (\" + item.getName() + \").\";"
            + "  }"
            + "}";

    private static final String EVIL_SRC =
            "package sandboxdemo;"
            + "import com.xai.dungeonmaster.plugin.ItemEffect;"
            + "import com.xai.dungeonmaster.Adventurer;"
            + "import com.xai.dungeonmaster.DungeonMasterEngine;"
            + "import com.xai.dungeonmaster.Item;"
            + "public class EvilEffect implements ItemEffect {"
            + "  public String id() { return \"SANDBOX_EVIL\"; }"
            + "  public String execute(DungeonMasterEngine engine, Adventurer user, Item item) {"
            + "    try { Runtime.getRuntime().exec(new String[]{\"echo\",\"pwned\"}); } catch (Exception e) {}"
            + "    return \"evil\";"
            + "  }"
            + "}";

    @AfterEach
    void reset() {
        ItemEffectRegistry.clearForTests();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Compile one plugin source into a fresh classes dir; returns that dir. */
    private static Path compile(Path work, String className, String source) throws IOException {
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        Path srcDir = Files.createDirectories(work.resolve("src"));
        Path outDir = Files.createDirectories(work.resolve("classes"));
        String pkg = className.substring(0, className.lastIndexOf('.')).replace('.', '/');
        String simple = className.substring(className.lastIndexOf('.') + 1);
        Path pkgDir = Files.createDirectories(srcDir.resolve(pkg));
        Path srcFile = pkgDir.resolve(simple + ".java");
        Files.writeString(srcFile, source);
        int rc = jc.run(null, null, System.err,
                "-classpath", coreClasspath(),
                "-d", outDir.toString(),
                srcFile.toString());
        if (rc != 0) {
            throw new IOException("in-JVM compilation failed (rc=" + rc + ") for " + className);
        }
        return outDir;
    }

    /** Classpath entry that contains the core plugin classes (ItemEffect, Item, ...). */
    private static String coreClasspath() {
        try {
            return new File(ItemEffect.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getPath();
        } catch (Exception e) {
            return System.getProperty("java.class.path");
        }
    }

    private static Path buildJar(Path dir, String jarName, String entryClass, Path classesDir) throws IOException {
        Files.createDirectories(dir);
        Path jar = dir.resolve(jarName);
        String manifest = "id: \"sandbox-test\"\n"
                + "version: \"1.0.0\"\n"
                + "minEngineVersion: \"1.0.0\"\n"
                + "entryClasses:\n  - \"" + entryClass + "\"\n";
        try (OutputStream os = Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(os)) {
            jos.putNextEntry(new JarEntry("plugin.yaml"));
            jos.write(manifest.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
            try (Stream<Path> files = Files.walk(classesDir)) {
                for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                    String entry = classesDir.relativize(p).toString().replace(File.separatorChar, '/');
                    jos.putNextEntry(new JarEntry(entry));
                    jos.write(Files.readAllBytes(p));
                    jos.closeEntry();
                }
            }
        }
        return jar;
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void benignPluginLoadsUnderSandbox(@TempDir Path tmp) throws IOException {
        assumeTrue(ToolProvider.getSystemJavaCompiler() != null, "JDK compiler required");
        Path classes = compile(tmp.resolve("good"), "sandboxdemo.GoodEffect", GOOD_SRC);
        Path jarDir = tmp.resolve("good-jar");
        buildJar(jarDir, "good.jar", "sandboxdemo.GoodEffect", classes);

        LoadReport r = PluginLoader.loadAll(jarDir, SignaturePolicy.DISABLED, SandboxPolicy.defaults());
        assertEquals(1, r.loaded.size(), "benign plugin should load; rejected=" + r.rejected + " failed=" + r.failed);
        assertTrue(r.rejected.isEmpty(), "no rejections expected: " + r.rejected);
        assertTrue(ItemEffectRegistry.isRegistered("SANDBOX_GOOD"));
    }

    @Test
    void maliciousPluginRejectedUnderSandbox(@TempDir Path tmp) throws IOException {
        assumeTrue(ToolProvider.getSystemJavaCompiler() != null, "JDK compiler required");
        Path classes = compile(tmp.resolve("evil"), "sandboxdemo.EvilEffect", EVIL_SRC);
        Path jarDir = tmp.resolve("evil-jar");
        buildJar(jarDir, "evil.jar", "sandboxdemo.EvilEffect", classes);

        LoadReport r = PluginLoader.loadAll(jarDir, SignaturePolicy.DISABLED, SandboxPolicy.defaults());
        assertTrue(r.loaded.isEmpty(), "malicious plugin must not load");
        assertEquals(1, r.rejected.size(), "malicious plugin must be rejected: " + r);
        assertTrue(r.rejected.get(0).contains("blocked by sandbox"), r.rejected.get(0));
        assertTrue(r.rejected.get(0).contains("Runtime"), r.rejected.get(0));
        assertFalse(ItemEffectRegistry.isRegistered("SANDBOX_EVIL"));
    }

    @Test
    void disabledSandboxAllowsEvenMaliciousPlugin(@TempDir Path tmp) throws IOException {
        assumeTrue(ToolProvider.getSystemJavaCompiler() != null, "JDK compiler required");
        Path classes = compile(tmp.resolve("evil2"), "sandboxdemo.EvilEffect", EVIL_SRC);
        Path jarDir = tmp.resolve("evil2-jar");
        buildJar(jarDir, "evil2.jar", "sandboxdemo.EvilEffect", classes);

        LoadReport r = PluginLoader.loadAll(jarDir, SignaturePolicy.DISABLED, SandboxPolicy.disabled());
        assertEquals(1, r.loaded.size(), "disabled sandbox should not scan; " + r);
        assertTrue(ItemEffectRegistry.isRegistered("SANDBOX_EVIL"));
    }
}
