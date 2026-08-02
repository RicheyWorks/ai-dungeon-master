package com.xai.dungeonmaster.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured admin ops audit lines (stdout/log). Never logs raw tokens or receipts.
 */
public final class AdminAudit {
    private static final Logger LOG = LoggerFactory.getLogger("dm.admin.audit");

    private AdminAudit() {}

    public static void log(String outcome, String path, String clientIp, String requestId, String detail) {
        String ip = clientIp == null || clientIp.isBlank() ? "-" : clientIp;
        String rid = requestId == null || requestId.isBlank() ? "-" : requestId;
        String d = detail == null ? "" : detail.replaceAll("[\\r\\n]+", " ");
        LOG.info("admin_audit outcome={} path={} ip={} requestId={} {}",
                outcome, path, ip, rid, d);
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
