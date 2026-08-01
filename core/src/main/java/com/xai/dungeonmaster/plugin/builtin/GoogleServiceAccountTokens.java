package com.xai.dungeonmaster.plugin.builtin;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mints short-lived Google OAuth access tokens from a service-account JSON key
 * for Android Publisher API calls (Play Billing receipt verification).
 *
 * Scope: {@code https://www.googleapis.com/auth/androidpublisher}
 */
public final class GoogleServiceAccountTokens {

    private static final String SCOPE = "https://www.googleapis.com/auth/androidpublisher";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final Pattern JSON_STRING = Pattern.compile("\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final Path jsonPath;
    private final HttpClient http;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public GoogleServiceAccountTokens(Path jsonPath, HttpClient http) {
        this.jsonPath = jsonPath;
        this.http = http;
    }

    /** Returns a non-blank access token or empty if minting fails. */
    public Optional<String> accessToken() {
        Cached c = cache.get();
        long now = Instant.now().getEpochSecond();
        if (c != null && c.expiresAtEpoch - 60 > now) {
            return Optional.of(c.token);
        }
        try {
            String body = Files.readString(jsonPath);
            String email = jsonString(body, "client_email").orElse("");
            String pem = jsonString(body, "private_key").orElse("");
            if (email.isBlank() || pem.isBlank()) {
                return Optional.empty();
            }
            pem = pem.replace("\\n", "\n");
            PrivateKey key = parsePkcs8Pem(pem);
            String jwt = signedJwt(email, key, now);
            String token = exchange(jwt);
            if (token == null || token.isBlank()) return Optional.empty();
            cache.set(new Cached(token, now + 3500));
            return Optional.of(token);
        } catch (Exception e) {
            System.err.println("[google_sa] token mint failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    private String exchange(String jwt) throws Exception {
        String form = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:jwt-bearer")
                + "&assertion=" + enc(jwt);
        HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("token HTTP " + res.statusCode() + ": " + res.body());
        }
        return jsonString(res.body() == null ? "" : res.body(), "access_token").orElse(null);
    }

    private static String signedJwt(String clientEmail, PrivateKey key, long now) throws Exception {
        String header = b64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64Url(("{"
                + "\"iss\":\"" + clientEmail + "\","
                + "\"scope\":\"" + SCOPE + "\","
                + "\"aud\":\"" + TOKEN_URL + "\","
                + "\"iat\":" + now + ","
                + "\"exp\":" + (now + 3600)
                + "}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + b64Url(sig.sign());
    }

    private static PrivateKey parsePkcs8Pem(String pem) throws Exception {
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(cleaned);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String b64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Optional<String> jsonString(String json, String field) {
        Matcher m = Pattern.compile(
                String.format(Locale.ROOT, "\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", field)).matcher(json);
        if (!m.find()) return Optional.empty();
        String raw = m.group(1);
        return Optional.of(raw.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\"));
    }

    private record Cached(String token, long expiresAtEpoch) {}
}
