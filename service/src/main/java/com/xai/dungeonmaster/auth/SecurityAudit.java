package com.xai.dungeonmaster.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured multi-tenant security events (ownership denials, scrape auth failures).
 * Never logs raw tokens or session JWTs — fingerprints only when needed.
 */
public final class SecurityAudit {
    private static final Logger LOG = LoggerFactory.getLogger("dm.security.audit");

    private SecurityAudit() {}

    public static void log(String outcome, String path, String clientIp, String requestId, String detail) {
        String ip = clientIp == null || clientIp.isBlank() ? "-" : clientIp;
        String rid = requestId == null || requestId.isBlank() ? "-" : requestId;
        String d = detail == null ? "" : detail.replaceAll("[\\r\\n]+", " ");
        LOG.info("security_audit outcome={} path={} ip={} requestId={} {}",
                outcome, path, ip, rid, d);
    }
}
