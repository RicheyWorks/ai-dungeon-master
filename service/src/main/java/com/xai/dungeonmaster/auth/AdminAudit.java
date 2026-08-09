package com.xai.dungeonmaster.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Structured admin ops audit lines (stdout/log). Never logs raw tokens or receipts.
 * Keeps a process-local ring for {@code GET /v2/admin/audit-events}.
 */
public final class AdminAudit {
    private static final Logger LOG = LoggerFactory.getLogger("dm.admin.audit");
    private static final int CAPACITY = 200;
    private static final Object LOCK = new Object();
    private static final Event[] RING = new Event[CAPACITY];
    private static int head;
    private static int size;
    private static final AtomicLong SEQ = new AtomicLong();

    private AdminAudit() {}

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
        LOG.info("admin_audit outcome={} path={} ip={} requestId={} {}",
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

    /** Test helper. */
    public static void clearForTests() {
        synchronized (LOCK) {
            for (int i = 0; i < CAPACITY; i++) RING[i] = null;
            head = 0;
            size = 0;
        }
        SEQ.set(0);
    }

    /** Fingerprint for logs — never the raw token. */
    public static String tokenFingerprint(String token) {
        if (token == null || token.isBlank()) return "none";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(token.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8 && i < dig.length; i++) {
                sb.append(String.format("%02x", dig[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "err";
        }
    }
}
