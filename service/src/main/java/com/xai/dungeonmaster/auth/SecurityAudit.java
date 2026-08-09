package com.xai.dungeonmaster.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Structured multi-tenant security events (ownership denials, scrape auth failures).
 * Never logs raw tokens or session JWTs — fingerprints only when needed.
 * Keeps a process-local ring of recent events for ops ({@code GET /v2/admin/security-events}).
 */
public final class SecurityAudit {
    private static final Logger LOG = LoggerFactory.getLogger("dm.security.audit");
    private static final int CAPACITY = 200;
    private static final Object LOCK = new Object();
    private static final Event[] RING = new Event[CAPACITY];
    private static int head; // next write index
    private static int size;
    private static final AtomicLong SEQ = new AtomicLong();

    private SecurityAudit() {}

    public record Event(
            long id,
            long atEpochMs,
            String outcome,
            String path,
            String clientIp,
            String requestId,
            String detail
    ) {}

    public static void log(String outcome, String path, String clientIp, String requestId, String detail) {
        String ip = clientIp == null || clientIp.isBlank() ? "-" : clientIp;
        String rid = requestId == null || requestId.isBlank() ? "-" : requestId;
        String d = detail == null ? "" : detail.replaceAll("[\\r\\n]+", " ");
        String p = path == null ? "-" : path;
        String o = outcome == null ? "-" : outcome;
        LOG.info("security_audit outcome={} path={} ip={} requestId={} {}",
                o, p, ip, rid, d);
        Event e = new Event(SEQ.incrementAndGet(), System.currentTimeMillis(), o, p, ip, rid, d);
        synchronized (LOCK) {
            RING[head] = e;
            head = (head + 1) % CAPACITY;
            if (size < CAPACITY) size++;
        }
    }

    /** Newest first. Cap 1–200. */
    public static List<Event> recent(int limit) {
        int cap = Math.min(CAPACITY, Math.max(1, limit <= 0 ? 50 : limit));
        synchronized (LOCK) {
            List<Event> out = new ArrayList<>(Math.min(cap, size));
            for (int i = 0; i < size && out.size() < cap; i++) {
                int idx = (head - 1 - i + CAPACITY) % CAPACITY;
                Event e = RING[idx];
                if (e != null) out.add(e);
            }
            return Collections.unmodifiableList(out);
        }
    }

    /** Test helper — clear ring between tests. */
    public static void clearForTests() {
        synchronized (LOCK) {
            for (int i = 0; i < CAPACITY; i++) RING[i] = null;
            head = 0;
            size = 0;
        }
        SEQ.set(0);
    }
}
