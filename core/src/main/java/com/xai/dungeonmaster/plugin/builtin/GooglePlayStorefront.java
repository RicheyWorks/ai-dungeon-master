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
 * Google Play storefront ({@code id = "google_play"}).
 *
 * <p><b>Live mode</b> — when {@code STOREFRONT_GOOGLE_ACCESS_TOKEN} (and package
 * name) are set, verifies purchase tokens against the Android Publisher API:
 * {@code GET …/applications/{package}/purchases/products/{sku}/tokens/{token}}.
 * Receipts are JSON:
 * <pre>{@code {"packageName":"…","productId":"…","purchaseToken":"…"}}</pre>
 *
 * <p><b>Sandbox mode</b> (default without a live token) — accepts HMAC receipts
 * signed with {@code STOREFRONT_GOOGLE_SECRET} (same shape as {@link DevStorefront}),
 * so CI and local Android clients can exercise the grant loop without calling Google.
 *
 * Access tokens are expected to be supplied by the operator (short-lived OAuth);
 * minting service-account JWTs is left to the deploy environment.
 */
public final class GooglePlayStorefront implements StorefrontIntegration {

    public static final String ID = "google_play";

    private static final Pattern JSON_STRING = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]*)\"");

    private final String packageName;
    private final String accessToken;
    private final byte[] sandboxSecret;
    private final HttpClient http;
    private final boolean live;

    public GooglePlayStorefront() {
        this(
                env("STOREFRONT_GOOGLE_PACKAGE_NAME", ""),
                env("STOREFRONT_GOOGLE_ACCESS_TOKEN", ""),
                env("STOREFRONT_GOOGLE_SECRET", "google-play-sandbox-insecure-secret").getBytes(StandardCharsets.UTF_8),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** Test / embedder constructor. */
    public GooglePlayStorefront(String packageName, String accessToken, byte[] sandboxSecret, HttpClient http) {
        this.packageName = packageName == null ? "" : packageName.trim();
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.sandboxSecret = sandboxSecret;
        this.http = http;
        this.live = !this.accessToken.isBlank() && !this.packageName.isBlank();
    }

    @Override public String id() { return ID; }

    @Override
    public String displayName() {
        return live ? "Google Play (live)" : "Google Play (sandbox HMAC)";
    }

    /** Mint a sandbox receipt for local/CI use. */
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
        if (trimmed.startsWith("{")) {
            return verifyJsonReceipt(trimmed);
        }
        // Sandbox HMAC (or client sent bare signed form)
        return HmacReceipts.verify(trimmed, sandboxSecret);
    }

    private boolean verifyJsonReceipt(String json) {
        Optional<String> productId = jsonString(json, "productId");
        Optional<String> token = jsonString(json, "purchaseToken");
        Optional<String> pkg = jsonString(json, "packageName");
        if (productId.isEmpty() || token.isEmpty()) return false;

        if (live) {
            String usePkg = pkg.filter(p -> !p.isBlank()).orElse(packageName);
            if (usePkg.isBlank()) return false;
            if (!packageName.isBlank() && !packageName.equals(usePkg)) return false;
            return verifyWithGoogle(usePkg, productId.get(), token.get());
        }

        // Sandbox: purchaseToken itself must be an HMAC receipt for productId
        if (!HmacReceipts.verify(token.get(), sandboxSecret)) return false;
        String fromBody = HmacReceipts.productIdFromReceipt(token.get());
        return productId.get().equals(fromBody);
    }

    private boolean verifyWithGoogle(String pkg, String productId, String purchaseToken) {
        try {
            String url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
                    + enc(pkg)
                    + "/purchases/products/"
                    + enc(productId)
                    + "/tokens/"
                    + enc(purchaseToken);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return false;
            String body = res.body() == null ? "" : res.body();
            // purchaseState 0 = purchased
            Optional<String> state = jsonNumberOrString(body, "purchaseState");
            return state.isEmpty() || "0".equals(state.get());
        } catch (Exception e) {
            System.err.println("[google_play] verify failed: " + e.getMessage());
            return false;
        }
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
        return new Identity(null, "Google Play", false);
    }

    @Override public void unlockAchievement(String achievementId) { /* client-side Play Games */ }
    @Override public void submitLeaderboard(String boardId, long score) { /* client-side */ }

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

    private static Optional<String> jsonString(String json, String field) {
        Matcher m = Pattern.compile(String.format(Locale.ROOT, "\"%s\"\\s*:\\s*\"([^\"]*)\"", field)).matcher(json);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static Optional<String> jsonNumberOrString(String json, String field) {
        Matcher s = Pattern.compile(String.format(Locale.ROOT, "\"%s\"\\s*:\\s*\"([^\"]*)\"", field)).matcher(json);
        if (s.find()) return Optional.of(s.group(1));
        Matcher n = Pattern.compile(String.format(Locale.ROOT, "\"%s\"\\s*:\\s*(-?\\d+)", field)).matcher(json);
        return n.find() ? Optional.of(n.group(1)) : Optional.empty();
    }

    private static String env(String name, String defaultValue) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) v = System.getProperty(name);
        return (v == null || v.isBlank()) ? defaultValue : v.trim();
    }
}
