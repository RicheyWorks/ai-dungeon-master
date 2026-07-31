package com.xai.dungeonmaster.plugin;

import java.util.List;

/**
 * Static-sandbox policy for code-bearing plugins: a denylist of forbidden
 * class-name prefixes (in JVM internal form, {@code /}-separated) plus an
 * on/off switch. {@link SandboxVerifier} scans each plugin-defined class's
 * constant pool (and method flags) and rejects any that reference a denied API
 * or declare {@code native} methods before the class is instantiated.
 *
 * The default denylist blocks the obvious escape hatches — process execution,
 * reflection, raw networking, filesystem access, class-loader tricks, JNI
 * (via native methods), JDK internals, RMI/JMX, and scripting — while leaving
 * normal computation (collections, math, strings, streams, lambdas) untouched.
 *
 * This is a load-time static check plus a runtime call timeout
 * ({@link PluginCallGuard}), not a full OS jail. Dedicated process / seccomp
 * isolation remains available as a further hardening step.
 */
public final class SandboxPolicy {

    private static final List<String> DEFAULT_DENY = List.of(
            // Process / exec
            "java/lang/Runtime",
            "java/lang/ProcessBuilder",
            "java/lang/ProcessImpl",
            "java/lang/ProcessHandle",
            // Reflection / classloading escapes
            "java/lang/reflect/",
            "java/lang/ClassLoader",
            "java/security/SecureClassLoader",
            "jdk/internal/reflect/",
            // I/O and network
            "java/net/",
            "java/nio/file/",
            "java/io/File",
            "java/io/FileInputStream",
            "java/io/FileOutputStream",
            "java/io/RandomAccessFile",
            "java/nio/channels/FileChannel",
            "java/nio/channels/SocketChannel",
            "java/nio/channels/ServerSocketChannel",
            "java/nio/channels/AsynchronousSocketChannel",
            // Scripting / management / remoting
            "javax/script/",
            "javax/management/",
            "java/rmi/",
            "javax/rmi/",
            // Internals / unsafe
            "sun/",
            "jdk/internal/",
            "com/sun/jndi/",
            "java/lang/instrument/"
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
