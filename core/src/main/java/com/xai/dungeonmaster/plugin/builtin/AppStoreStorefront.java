package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;

import java.net.URI;
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
 * Apple App Store storefront ({@code id = "app_store"}).
 *
 * <p><b>Live mode</b> — when {@code STOREFRONT_APPLE_SHARED_SECRET} is set,
 * posts the receipt to Apple's {@code verifyReceipt} endpoint (production first,
 * then sandbox on status 21007). Receipts are JSON:
 * <pre>{@code {"receiptData":"<base64>","productId":"optional-sku-hint"}}</pre>
 * or a raw base64 App Store receipt string.
 *
 * <p><b>Sandbox mode</b> (no shared secret) — HMAC receipts via
 * {@code STOREFRONT_APPLE_SECRET}, same shape as {@link DevStorefront}.
 */
public final class AppStoreStorefront implements StorefrontIntegration {

    public static final String ID = "app_store";

    private static final String PROD_URL = "https://buy.itunes.apple.com/verifyReceipt";
    private static final String SANDBOX_URL = "https://sandbox.itunes.apple.com/verifyReceipt";

    private final String sharedSecret;
    private final String bundleId;
    private final byte[] sandboxSecret;
    private final HttpClient http;
    private final boolean live;

    public AppStoreStorefront() {
        this(
                env("STOREFRONT_APPLE_SHARED_SECRET", ""),
                env("STOREFRONT_APPLE_BUNDLE_ID", ""),
                env("STOREFRONT_APPLE_SECRET", "app-store-sandbox-insecure-secret").getBytes(StandardCharsets.UTF_8),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public AppStoreStorefront(String sharedSecret, String bundleId, byte[] sandboxSecret, HttpClient http) {
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret.trim();
        this.bundleId = bundleId == null ? "" : bundleId.trim();
        this.sandboxSecret = sandboxSecret;
        this.http = http;
        this.live = !this.sharedSecret.isBlank();
    }

    @Override public String id() { return ID; }

    @Override
    public String displayName() {
        return live ? "App Store (live verifyReceipt)" : "App Store (sandbox HMAC)";
    }

    public String signSandboxReceipt(String productId) {
        return HmacReceipts.sign(productId, sandboxSecret);
    }

    public boolean isLive() {
        return live;
    }

    @Override
    public boolean verifyReceipt(String receipt) {
        if (receipt == null || receipt.isBlank()) return false;
        String trimmed = receipt.trim();

        if (!live) {
            if (trimmed.startsWith("{")) {
                Optional<String> token = jsonString(trimmed, "purchaseToken")
                        .or(() -> jsonString(trimmed, "receiptData"))
                        .or(() -> jsonString(trimmed, "receipt"));
                if (token.isPresent() && HmacReceipts.verify(token.get(), sandboxSecret)) {
                    Optional<String> productId = jsonString(trimmed, "productId");
                    if (productId.isEmpty()) return true;
                    return productId.get().equals(HmacReceipts.productIdFromReceipt(token.get()));
                }
                return false;
            }
            return HmacReceipts.verify(trimmed, sandboxSecret);
        }

        String receiptData = trimmed;
        String expectedProduct = null;
        if (trimmed.startsWith("{")) {
            receiptData = jsonString(trimmed, "receiptData")
                    .or(() -> jsonString(trimmed, "receipt"))
                    .orElse("");
            expectedProduct = jsonString(trimmed, "productId").orElse(null);
            if (receiptData.isBlank()) return false;
        }
        return verifyWithApple(receiptData, expectedProduct);
    }

    private boolean verifyWithApple(String receiptData, String expectedProduct) {
        try {
            String body = buildApplePayload(receiptData);
            HttpResponse<String> prod = post(PROD_URL, body);
            String responseBody = prod.body() == null ? "" : prod.body();
            int status = statusOf(responseBody);
            if (status == 21007) {
                // Sandbox receipt sent to production — retry sandbox.
                HttpResponse<String> sand = post(SANDBOX_URL, body);
                responseBody = sand.body() == null ? "" : sand.body();
                status = statusOf(responseBody);
            }
            if (status != 0) return false;
            if (!bundleId.isBlank()) {
                Optional<String> bid = jsonString(responseBody, "bundle_id");
                if (bid.isPresent() && !bundleId.equals(bid.get())) {
                    // Also check nested receipt.bundle_id
                    if (!responseBody.contains("\"bundle_id\":\"" + bundleId + "\"")) {
                        return false;
                    }
                }
            }
            if (expectedProduct != null && !expectedProduct.isBlank()) {
                return responseBody.contains("\"product_id\":\"" + expectedProduct + "\"")
                        || responseBody.contains("\"productId\":\"" + expectedProduct + "\"");
            }
            return true;
        } catch (Exception e) {
            System.err.println("[app_store] verify failed: " + e.getMessage());
            return false;
        }
    }

    private String buildApplePayload(String receiptData) {
        String escaped = receiptData
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        StringBuilder sb = new StringBuilder(128 + escaped.length());
        sb.append("{\"receipt-data\":\"").append(escaped).append("\"");
        sb.append(",\"password\":\"").append(sharedSecret.replace("\"", "")).append("\"");
        sb.append(",\"exclude-old-transactions\":true}");
        return sb.toString();
    }

    private HttpResponse<String> post(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static int statusOf(String body) {
        Matcher n = Pattern.compile("\"status\"\\s*:\\s*(-?\\d+)").matcher(body);
        if (n.find()) {
            try {
                return Integer.parseInt(n.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public PurchaseFlow startPurchase(String productId) {
        final String receipt = signSandboxReceipt(productId);
        return new PurchaseFlow() {
            @Override public boolean isComplete() { return true; }
            @Override public boolean wasSuccessful() { return !live; }
            @Override public String receipt() { return live ? null : receipt; }
        };
    }

    @Override
    public Identity currentIdentity() {
        return new Identity(null, "App Store", false);
    }

    @Override public void unlockAchievement(String achievementId) { /* client Game Center */ }
    @Override public void submitLeaderboard(String boardId, long score) { /* client */ }

    @Override
    public CloudSaveHandle openCloudSave(String slot) {
        return new CloudSaveHandle() {
            @Override public byte[] read() { return new byte[0]; }
            @Override public void write(byte[] data) { /* no-op on server */ }
            @Override public boolean isAvailable() { return false; }
        };
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
