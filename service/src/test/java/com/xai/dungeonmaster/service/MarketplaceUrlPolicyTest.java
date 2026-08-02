package com.xai.dungeonmaster.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceUrlPolicyTest {

    @Test
    void rejectsLoopbackAndLinkLocalLiterals() {
        assertThrows(IllegalArgumentException.class,
                () -> MarketplaceUrlPolicy.assertSafeRemoteUrl("http://127.0.0.1/pack.zip"));
        assertThrows(IllegalArgumentException.class,
                () -> MarketplaceUrlPolicy.assertSafeRemoteUrl("https://169.254.169.254/latest/meta-data"));
        assertThrows(IllegalArgumentException.class,
                () -> MarketplaceUrlPolicy.assertSafeRemoteUrl("http://192.168.1.10/x"));
        assertThrows(IllegalArgumentException.class,
                () -> MarketplaceUrlPolicy.assertSafeRemoteUrl("http://10.0.0.5/x"));
        assertThrows(IllegalArgumentException.class,
                () -> MarketplaceUrlPolicy.assertSafeRemoteUrl("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class,
                () -> MarketplaceUrlPolicy.assertSafeRemoteUrl("ftp://example.com/x"));
    }

    @Test
    void rejectsLocalhostHostname() {
        assertThrows(IllegalArgumentException.class,
                () -> MarketplaceUrlPolicy.assertSafeRemoteUrl("http://localhost/pack.zip"));
    }

    @Test
    void blockedAddressHelper() throws Exception {
        assertTrue(MarketplaceUrlPolicy.isBlocked(InetAddress.getByName("127.0.0.1")));
        assertTrue(MarketplaceUrlPolicy.isBlocked(InetAddress.getByName("10.1.2.3")));
        assertTrue(MarketplaceUrlPolicy.isBlocked(InetAddress.getByName("192.168.0.1")));
        assertTrue(MarketplaceUrlPolicy.isBlocked(InetAddress.getByName("169.254.169.254")));
    }
}
