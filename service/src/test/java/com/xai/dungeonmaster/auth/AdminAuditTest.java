package com.xai.dungeonmaster.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AdminAuditTest {

    @Test
    void tokenFingerprintIsStableAndNeverRaw() {
        String raw = "ops-secret-token-for-audit-test!!";
        String fp = AdminAudit.tokenFingerprint(raw);
        assertNotNull(fp);
        assertEquals(16, fp.length());
        assertFalse(fp.contains(raw));
        assertEquals(fp, AdminAudit.tokenFingerprint(raw));
        assertEquals("none", AdminAudit.tokenFingerprint(null));
        assertEquals("none", AdminAudit.tokenFingerprint("  "));
    }
}
