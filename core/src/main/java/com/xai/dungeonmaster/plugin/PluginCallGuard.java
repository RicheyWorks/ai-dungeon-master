package com.xai.dungeonmaster.plugin;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime half of plugin isolation: every SPI dispatch (item/spell effects, …)
 * runs under a wall-clock timeout on a daemon worker thread. A hung or
 * pathological mod cannot freeze the game loop forever.
 *
 * Complements the load-time {@link SandboxVerifier} (which blocks dangerous APIs
 * at define-time). Neither is a full OS jail; together they cover the two most
 * common failure modes — malicious capability and runaway execution.
 *
 * Config (system properties, overridable from the service layer):
 * <ul>
 *   <li>{@code game.plugins.call.timeout-ms} — wall timeout, default {@code 2000}</li>
 *   <li>{@code game.plugins.call.guard} — {@code true}/{@code false}, default on</li>
 * </ul>
 */
public final class PluginCallGuard {

    public static final String PROP_TIMEOUT_MS = "game.plugins.call.timeout-ms";
    public static final String PROP_ENABLED = "game.plugins.call.guard";
    public static final long DEFAULT_TIMEOUT_MS = 2000L;

    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();
    private static final ThreadFactory FACTORY = r -> {
        Thread t = new Thread(r, "plugin-call-" + THREAD_SEQ.incrementAndGet());
        t.setDaemon(true);
        return t;
    };

    private PluginCallGuard() {}

    /**
     * Run {@code call} under the configured timeout. On timeout or unexpected
     * failure, returns {@code onFailure} applied to a short reason string.
     */
    public static String run(String pluginId, Callable<String> call, java.util.function.Function<String, String> onFailure) {
        if (call == null) {
            return onFailure != null ? onFailure.apply("null call") : "";
        }
        if (!isEnabled()) {
            try {
                String r = call.call();
                return r != null ? r : "";
            } catch (Exception e) {
                return fail(onFailure, "threw " + e.getClass().getSimpleName());
            }
        }
        long timeoutMs = timeoutMs();
        ExecutorService pool = Executors.newSingleThreadExecutor(FACTORY);
        Future<String> future = pool.submit(call);
        try {
            String r = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return r != null ? r : "";
        } catch (TimeoutException te) {
            future.cancel(true);
            System.err.println("WARN: plugin call timed out after " + timeoutMs + "ms"
                    + (pluginId != null ? " (" + pluginId + ")" : ""));
            return fail(onFailure, "timed out after " + timeoutMs + "ms");
        } catch (ExecutionException ee) {
            Throwable c = ee.getCause() != null ? ee.getCause() : ee;
            System.err.println("WARN: plugin call failed"
                    + (pluginId != null ? " (" + pluginId + ")" : "")
                    + ": " + c.getClass().getSimpleName() + ": " + c.getMessage());
            return fail(onFailure, "threw " + c.getClass().getSimpleName());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return fail(onFailure, "interrupted");
        } finally {
            pool.shutdownNow();
        }
    }

    /** Convenience: wrap with a default "mod fizzled" message. */
    public static String run(String pluginId, Callable<String> call) {
        return run(pluginId, call, reason ->
                "The magic fizzles (" + reason
                        + (pluginId != null ? "; mod " + pluginId : "")
                        + ").");
    }

    public static boolean isEnabled() {
        String v = System.getProperty(PROP_ENABLED);
        if (v == null || v.isBlank()) return true;
        return !"false".equalsIgnoreCase(v.trim()) && !"0".equals(v.trim()) && !"off".equalsIgnoreCase(v.trim());
    }

    public static long timeoutMs() {
        String v = System.getProperty(PROP_TIMEOUT_MS);
        if (v == null || v.isBlank()) return DEFAULT_TIMEOUT_MS;
        try {
            long ms = Long.parseLong(v.trim());
            return ms < 50L ? 50L : ms;
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    private static String fail(java.util.function.Function<String, String> onFailure, String reason) {
        return onFailure != null ? onFailure.apply(reason) : "";
    }
}
