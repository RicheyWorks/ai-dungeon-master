package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Steam storefront ({@code id = "steam"}).
 *
 * <p><b>Live mode</b> — when {@code STOREFRONT_STEAM_PUBLISHER_KEY} and
 * {@code STOREFRONT_STEAM_APP_ID} are set, verifies microtransaction orders via
 * Steam Partner Web API {@code ISteamMicroTxn[Sandbox]/QueryTxn}:
 * <pre>{@code {"orderId":"…","steamId":"…","productId":"…"}}</pre>
 * Set {@code STOREFRONT_STEAM_SANDBOX=true} to hit {@code ISteamMicroTxnSandbox}.
 *
 * <p><b>Sandbox HMAC mode</b> (default without a publisher key) — accepts HMAC
 * receipts signed with {@code STOREFRONT_STEAM_SECRET}, optionally wrapped as JSON
 * with {@code orderId} holding the HMAC token (same pattern as Play/App Store).
 */
public final class SteamStorefront implements StorefrontIntegration {

    public static final String ID = "steam";

    private final String publisherKey;
    private final String appId;
    private final boolean partnerSandbox;
    private final byte[] hmacSecret;
    private final HttpClient http;
    private final boolean live;

    public SteamStorefront() {
        this(
                env("STOREFRONT_STEAM_PUBLISHER_KEY", ""),
                env("STOREFRONT_STEAM_APP_ID", ""),
                truthy(env("STOREFRONT_STEAM_SANDBOX", "false")),
                env("STOREFRONT_STEAM_SECRET", "steam-sandbox-insecure-secret").getBytes(StandardCharsets.UTF_8),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public SteamStorefront(
            String publisherKey,
            String appId,
            boolean partnerSandbox,
            byte[] hmacSecret,
            HttpClient http) {
        this.publisherKey = publisherKey == null ? "" : publisherKey.trim();
        this.appId = appId == null ? "" : appId.trim();
        this.partnerSandbox = partnerSandbox;
        this.hmacSecret = hmacSecret;
        this.http = http;
        this.live = !this.publisherKey.isBlank() && !this.appId.isBlank();
    }

    @Override public String id() { return ID; }

    @Override
    public String displayName() {
        if (live) {
            return partnerSandbox ? "Steam (MicroTxn sandbox API)" : "Steam (MicroTxn live API)";
        }
        return "Steam (HMAC sandbox)";
    }

    public boolean isLive() {
        return live;
    }

    public String signSandboxReceipt(String productId) {
        return HmacReceipts.sign(productId, hmacSecret);
    }

    @Override
    public boolean verifyReceipt(String receipt) {
        if (receipt == null || receipt.isBlank()) return false;
        String trimmed = receipt.trim();

        if (!live) {
            if (trimmed.startsWith("{")) {
                Optional<String> token = jsonString(trimmed, "orderId")
                        .or(() -> jsonString(trimmed, "purchaseToken"))
                        .or(() -> jsonString(trimmed, "receipt"));
                if (token.isEmpty()) return false;
                if (!HmacReceipts.verify(token.get(), hmacSecret)) return false;
                Optional<String> productId = jsonString(trimmed, "productId");
                if (productId.isEmpty()) return true;
                return productId.get().equals(HmacReceipts.productIdFromReceipt(token.get()));
            }
            return HmacReceipts.verify(trimmed, hmacSecret);
        }

        // Live partner API
        String orderId;
        String expectedProduct = null;
        if (trimmed.startsWith("{")) {
            orderId = jsonString(trimmed, "orderId").orElse("");
            expectedProduct = jsonString(trimmed, "productId").orElse(null);
            if (orderId.isBlank()) return false;
        } else {
            orderId = trimmed;
        }
        return verifyWithSteam(orderId, expectedProduct);
    }

    private boolean verifyWithSteam(String orderId, String expectedProduct) {
        try {
            String iface = partnerSandbox ? "ISteamMicroTxnSandbox" : "ISteamMicroTxn";
            String url = "https://partner.steam-api.com/" + iface + "/QueryTxn/v3/"
                    + "?key=" + enc(publisherKey)
                    + "&appid=" + enc(appId)
                    + "&orderid=" + enc(orderId);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return false;
            String body = res.body() == null ? "" : res.body();
            boolean ok = body.contains("\"result\":\"OK\"")
                    || body.contains("\"result\": \"OK\"")
                    || body.matches("(?s).*\"result\"\\s*:\\s*1\\b.*");
            if (!ok) return false;
            if (expectedProduct != null && !expectedProduct.isBlank()) {
                return body.contains(expectedProduct)
                        || body.toLowerCase(Locale.ROOT).contains(expectedProduct.toLowerCase(Locale.ROOT));
            }
            return true;
        } catch (Exception e) {
            System.err.println("[steam] verify failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void afterGrant(String productId, String receipt) {
        if (!live || receipt == null || receipt.isBlank()) return;
        String orderId;
        String trimmed = receipt.trim();
        if (trimmed.startsWith("{")) {
            orderId = jsonString(trimmed, "orderId").orElse("");
        } else {
            orderId = trimmed;
        }
        if (orderId.isBlank()) return;
        finalizeTxn(orderId);
    }

    /** Steam Partner FinalizeTxn so the microtransaction is settled after grant. */
    private void finalizeTxn(String orderId) {
        try {
            String iface = partnerSandbox ? "ISteamMicroTxnSandbox" : "ISteamMicroTxn";
            // v2 FinalizeTxn is form POST
            String form = "key=" + enc(publisherKey)
                    + "&appid=" + enc(appId)
                    + "&orderid=" + enc(orderId);
            HttpRequest req = HttpRequest.newBuilder(
                            URI.create("https://partner.steam-api.com/" + iface + "/FinalizeTxn/v2/"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) {
                System.err.println("[steam] FinalizeTxn HTTP " + res.statusCode()
                        + " " + (res.body() == null ? "" : res.body()));
            }
        } catch (Exception e) {
            System.err.println("[steam] FinalizeTxn failed: " + e.getMessage());
        }
    }

    @Override
    public PurchaseFlow startPurchase(String productId) {
        final String receipt = signSandboxReceipt(productId);
        return new PurchaseFlow() {
            @Override public boolean isComplete() { return true; }
            @Override public boolean wasSuccessful() { return !live; }
            @Override public String receipt() {
                if (live) return null;
                return "{\"orderId\":\"" + receipt + "\",\"productId\":\"" + productId + "\"}";
            }
        };
    }

    @Override
    public Identity currentIdentity() {
        return new Identity(null, "Steam", false);
    }

    @Override public void unlockAchievement(String achievementId) { /* Steamworks client */ }
    @Override public void submitLeaderboard(String boardId, long score) { /* Steamworks client */ }

    @Override
    public CloudSaveHandle openCloudSave(String slot) {
        return new CloudSaveHandle() {
            @Override public byte[] read() { return new byte[0]; }
            @Override public void write(byte[] data) { /* no-op on server */ }
            @Override public boolean isAvailable() { return false; }
        };
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean truthy(String v) {
        if (v == null) return false;
        String t = v.trim().toLowerCase(Locale.ROOT);
        return t.equals("1") || t.equals("true") || t.equals("yes") || t.equals("on");
    }

    private static Optional<String> jsonString(String json, String field) {
        Matcher m = Pattern.compile(String.format(Locale.ROOT, "\"%s\"\\s*:\\s*\"([^\"]*)\"", field)).matcher(json);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) v = System.getProperty(name);
        return (v == null || v.isBlank()) ? defaultValue : v.trim();
    }
}
