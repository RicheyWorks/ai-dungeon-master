package com.xai.dungeonmaster.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * SSRF guard for marketplace remote fetches (index + pack zips).
 *
 * <p>Allows only {@code http}/{@code https} URLs whose resolved address is
 * public (not loopback, link-local, site-local, or multicast). {@code file:}
 * and bare filesystem paths are rejected for remote downloads — local packs
 * are discovered on disk under {@code game.content.packs.dir} instead.
 */
public final class MarketplaceUrlPolicy {

    private MarketplaceUrlPolicy() {}

    /**
     * Validate a remote download URL. Throws {@link IllegalArgumentException}
     * when the URL must not be fetched by the engine.
     */
    public static void assertSafeRemoteUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Download URL is empty");
        }
        String url = raw.trim();
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("file:") || lower.startsWith("jar:") || lower.startsWith("ftp:")) {
            throw new IllegalArgumentException("Unsupported download scheme (file/jar/ftp not allowed for remote packs)");
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("Unsupported download URL scheme (https required for remote packs)");
        }

        final URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed download URL");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Download URL missing host");
        }
        String host = uri.getHost().trim();
        // Literal IPs and hostnames both go through InetAddress
        try {
            for (InetAddress addr : InetAddress.getAllByName(host)) {
                if (isBlocked(addr)) {
                    throw new IllegalArgumentException(
                            "Download host resolves to a blocked address (" + addr.getHostAddress() + ")");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Download host could not be resolved: " + host);
        }
    }

    static boolean isBlocked(InetAddress addr) {
        if (addr == null) return true;
        return addr.isAnyLocalAddress()
                || addr.isLoopbackAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()
                || addr.isMulticastAddress()
                || isCgNat(addr)
                || isMetadataRange(addr);
    }

    /** Carrier-grade NAT 100.64.0.0/10. */
    private static boolean isCgNat(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length != 4) return false;
        int first = b[0] & 0xff;
        int second = b[1] & 0xff;
        return first == 100 && second >= 64 && second <= 127;
    }

    /** AWS/GCP-style link-local metadata often appears as 169.254.169.254 (also link-local). */
    private static boolean isMetadataRange(InetAddress addr) {
        byte[] b = addr.getAddress();
        if (b.length != 4) return false;
        // 169.254.0.0/16 already covered by isLinkLocalAddress; keep explicit for clarity
        return (b[0] & 0xff) == 169 && (b[1] & 0xff) == 254;
    }
}
