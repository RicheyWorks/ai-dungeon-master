package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google Play storefront ({@code id = "google_play"}).
 *
 * <p><b>Live mode</b> when package name is set and either:
 * <ul>
 *   <li>{@code STOREFRONT_GOOGLE_ACCESS_TOKEN} is set, or</li>
 *   <li>{@code STOREFRONT_GOOGLE_SERVICE_ACCOUNT_JSON} points at a service-account key
 *       (auto-mints Android Publisher OAuth tokens)</li>
 * </ul>
 * Receipts are JSON:
 * <pre>{@code {"packageName":"…","productId":"…","purchaseToken":"…"}}</pre>
 *
 * <p><b>Sandbox mode</b> (default) — HMAC receipts via {@code STOREFRONT_GOOGLE_SECRET}.
 */
public final class GooglePlayStorefront implements StorefrontIntegration {

    public static final String ID = "google_play";

    private final Supplier<String> packageName;
    private final Supplier<String> accessToken;
    private final Supplier<byte[]> sandboxSecret;
    private final HttpClient http;
    private final GoogleServiceAccountTokens serviceAccount;

    public GooglePlayStorefront() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.http = client;
        this.packageName = () -> env("STOREFRONT_GOOGLE_PACKAGE_NAME", "");
        this.accessToken = () -> env("STOREFRONT_GOOGLE_ACCESS_TOKEN", "");
        this.sandboxSecret = () -> env("STOREFRONT_GOOGLE_SECRET", "google-play-sandbox-insecure-secret")
                .getBytes(StandardCharsets.UTF_8);
        String saPath = env("STOREFRONT_GOOGLE_SERVICE_ACCOUNT_JSON", "");
        this.serviceAccount = saPath.isBlank() || !Files.isRegularFile(Path.of(saPath))
                ? null
                : new GoogleServiceAccountTokens(Path.of(saPath), client);
    }

    /** Test / embedder constructor (fixed credentials, no service account). */
    public GooglePlayStorefront(String packageName, String accessToken, byte[] sandboxSecret, HttpClient http) {
        this.packageName = () -> packageName == null ? "" : packageName.trim();
        this.accessToken = () -> accessToken == null ? "" : accessToken.trim();
        this.sandboxSecret = () -> sandboxSecret;
        this.http = http;
        this.serviceAccount = null;
    }

    @Override public String id() { return ID; }

    @Override
    public String displayName() {
        return isLive() ? "Google Play (live)" : "Google Play (sandbox HMAC)";
    }

    public String signSandboxReceipt(String productId) {
        return HmacReceipts.sign(productId, sandboxSecret.get());
    }

    public boolean isLive() {
        return !packageName.get().isBlank() && !resolveAccessToken().isBlank();
    }

    @Override
    public boolean verifyReceipt(String receipt) {
        if (receipt == null || receipt.isBlank()) return false;
        String trimmed = receipt.trim();
        if (trimmed.startsWith("{")) {
            return verifyJsonReceipt(trimmed);
        }
        return HmacReceipts.verify(trimmed, sandboxSecret.get());
    }

    private boolean verifyJsonReceipt(String json) {
        Optional<String> productId = jsonString(json, "productId");
        Optional<String> token = jsonString(json, "purchaseToken");
        Optional<String> pkg = jsonString(json, "packageName");
        if (productId.isEmpty() || token.isEmpty()) return false;

        if (isLive()) {
            String usePkg = pkg.filter(p -> !p.isBlank()).orElse(packageName.get());
            if (usePkg.isBlank()) return false;
            String configured = packageName.get();
            if (!configured.isBlank() && !configured.equals(usePkg)) return false;
            return verifyWithGoogle(usePkg, productId.get(), token.get());
        }

        if (!HmacReceipts.verify(token.get(), sandboxSecret.get())) return false;
        String fromBody = HmacReceipts.productIdFromReceipt(token.get());
        return productId.get().equals(fromBody);
    }

    private String resolveAccessToken() {
        String direct = accessToken.get();
        if (direct != null && !direct.isBlank()) return direct.trim();
        if (serviceAccount != null) {
            return serviceAccount.accessToken().orElse("");
        }
        return "";
    }

    private boolean verifyWithGoogle(String pkg, String productId, String purchaseToken) {
        try {
            String bearer = resolveAccessToken();
            if (bearer.isBlank()) return false;
            String url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
                    + enc(pkg)
                    + "/purchases/products/"
                    + enc(productId)
                    + "/tokens/"
                    + enc(purchaseToken);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + bearer)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return false;
            String body = res.body() == null ? "" : res.body();
            Optional<String> state = jsonNumberOrString(body, "purchaseState");
            boolean purchased = state.isEmpty() || "0".equals(state.get());
            if (purchased) {
                // Best-effort settle during verify (also run again in afterGrant).
                settleLivePurchase(pkg, productId, purchaseToken, body);
            }
            return purchased;
        } catch (Exception e) {
            System.err.println("[google_play] verify failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void afterGrant(String productId, String receipt) {
        if (!isLive() || receipt == null || !receipt.trim().startsWith("{")) return;
        String json = receipt.trim();
        Optional<String> token = jsonString(json, "purchaseToken");
        Optional<String> pkg = jsonString(json, "packageName");
        Optional<String> pid = jsonString(json, "productId");
        if (token.isEmpty()) return;
        String usePkg = pkg.filter(p -> !p.isBlank()).orElse(packageName.get());
        String usePid = pid.filter(p -> !p.isBlank()).orElse(productId == null ? "" : productId);
        if (usePkg.isBlank() || usePid.isBlank()) return;
        settleLivePurchase(usePkg, usePid, token.get(), null);
    }

    /**
     * Acknowledge (and optionally consume) a live Play purchase via the
     * Android Publisher API so the token cannot be re-granted forever and
     * consumables return to the store catalog.
     */
    private void settleLivePurchase(String pkg, String productId, String purchaseToken, String getBody) {
        if (!autoAcknowledge()) return;
        try {
            boolean alreadyAcked = getBody != null
                    && ("1".equals(jsonNumberOrString(getBody, "acknowledgementState").orElse("0")));
            if (!alreadyAcked) {
                postEmpty(publisherUrl(pkg, productId, purchaseToken, "acknowledge"));
            }
            if (autoConsume()) {
                postEmpty(publisherUrl(pkg, productId, purchaseToken, "consume"));
            }
        } catch (Exception e) {
            System.err.println("[google_play] settle failed: " + e.getMessage());
        }
    }

    private String publisherUrl(String pkg, String productId, String purchaseToken, String action) {
        return "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
                + enc(pkg)
                + "/purchases/products/"
                + enc(productId)
                + "/tokens/"
                + enc(purchaseToken)
                + ":"
                + action;
    }

    private void postEmpty(String url) throws Exception {
        String bearer = resolveAccessToken();
        if (bearer.isBlank()) return;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 300) {
            System.err.println("[google_play] " + url + " → HTTP " + res.statusCode()
                    + " " + (res.body() == null ? "" : res.body()));
        }
    }

    private static boolean autoAcknowledge() {
        String v = env("STOREFRONT_GOOGLE_AUTO_ACKNOWLEDGE", "true");
        return truthy(v);
    }

    private static boolean autoConsume() {
        return truthy(env("STOREFRONT_GOOGLE_AUTO_CONSUME", "false"));
    }

    private static boolean truthy(String v) {
        if (v == null) return false;
        String t = v.trim().toLowerCase(Locale.ROOT);
        return t.equals("1") || t.equals("true") || t.equals("yes") || t.equals("on");
    }

    @Override
    public PurchaseFlow startPurchase(String productId) {
        final String receipt = signSandboxReceipt(productId);
        return new PurchaseFlow() {
            @Override public boolean isComplete() { return true; }
            @Override public boolean wasSuccessful() { return !isLive(); }
            @Override public String receipt() { return isLive() ? null : receipt; }
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
