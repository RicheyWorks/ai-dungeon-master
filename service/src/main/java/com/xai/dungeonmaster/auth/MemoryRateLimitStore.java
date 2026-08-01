package com.xai.dungeonmaster.auth;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Process-local fixed-window counters (single node). */
public final class MemoryRateLimitStore implements RateLimitStore {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Result hit(String key) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartMs >= 60_000L) {
                return new Window(now, new AtomicInteger(0));
            }
            return existing;
        });
        int n = w.count.incrementAndGet();
        long retryAfterSec = Math.max(1L, (60_000L - (now - w.windowStartMs) + 999) / 1000L);
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> now - e.getValue().windowStartMs > 120_000L);
        }
        return new Result(n, retryAfterSec);
    }

    private static final class Window {
        final long windowStartMs;
        final AtomicInteger count;

        Window(long windowStartMs, AtomicInteger count) {
            this.windowStartMs = windowStartMs;
            this.count = count;
        }
    }
}
