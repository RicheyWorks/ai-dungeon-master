package com.xai.dungeonmaster.plugin;

import java.util.List;

/**
 * Static-sandbox policy for code-bearing plugins: a denylist of forbidden
 * class-name prefixes (in JVM internal form, {@code /}-separated) plus an
 * on/off switch. {@link SandboxVerifier} scans each plugin-defined class's
 * constant pool and rejects any that references a denied API before the class
 * is instantiated.
 *
 * The default denylist blocks the obvious escape hatches — process execution,
 * reflection, raw networking, filesystem access, JDK internals, and scripting —
 * while leaving normal computation (collections, math, strings, streams,
 * lambdas) untouched. It is a load-time static check, not a runtime jail:
 * it stops a mod from *referencing* these APIs, which is how they would be
 * invoked. Deeper isolation (a dedicated JVM / seccomp) is a later phase.
 */
public final class SandboxPolicy {

    private static final List<String> DEFAULT_DENY = List.of(
            "java/lang/Runtime",
            "java/lang/ProcessBuilder",
            "java/lang/ProcessImpl",
            "java/lang/reflect/",
            "java/net/",
            "java/nio/file/",
            "java/io/File",
            "javax/script/",
            "sun/",
            "jdk/internal/"
    );

    private final boolean enabled;
    private final List<String> deniedPrefixes;

    private SandboxPolicy(boolean enabled, List<String> deniedPrefixes) {
        this.enabled = enabled;
        this.deniedPrefixes = List.copyOf(deniedPrefixes);
    }

    /** Enabled with the standard denylist. */
    public static SandboxPolicy defaults() {
        return new SandboxPolicy(true, DEFAULT_DENY);
    }

    /** Disabled — no scanning (legacy behavior). */
    public static SandboxPolicy disabled() {
        return new SandboxPolicy(false, DEFAULT_DENY);
    }

    /** Enabled with a custom denylist. */
    public static SandboxPolicy of(List<String> deniedPrefixes) {
        return new SandboxPolicy(true, (deniedPrefixes == null || deniedPrefixes.isEmpty())
                ? DEFAULT_DENY : deniedPrefixes);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> deniedPrefixes() {
        return deniedPrefixes;
    }

    /** True if the given internal class name matches a denied prefix. */
    public boolean isDenied(String internalName) {
        if (internalName == null) return false;
        for (String prefix : deniedPrefixes) {
            if (internalName.startsWith(prefix)) return true;
        }
        return false;
    }
}
