package com.xai.dungeonmaster.config;

import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import com.xai.dungeonmaster.plugin.builtin.AppStoreStorefront;
import com.xai.dungeonmaster.plugin.builtin.GooglePlayStorefront;
import com.xai.dungeonmaster.plugin.builtin.SteamStorefront;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Maps {@code game.storefront.*} application properties onto the env/system
 * properties the storefront plugins read, then reloads SPI plugins so live
 * credentials take effect even if ServiceLoader ran early.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StorefrontConfig {

    @Value("${game.storefront.google.package-name:}")
    private String googlePackage;

    @Value("${game.storefront.google.access-token:}")
    private String googleAccessToken;

    @Value("${game.storefront.google.service-account-json:}")
    private String googleServiceAccountJson;

    @Value("${game.storefront.google.sandbox-secret:}")
    private String googleSandboxSecret;

    @Value("${game.storefront.apple.shared-secret:}")
    private String appleSharedSecret;

    @Value("${game.storefront.apple.bundle-id:}")
    private String appleBundleId;

    @Value("${game.storefront.apple.sandbox-secret:}")
    private String appleSandboxSecret;

    @Value("${game.storefront.steam.publisher-key:}")
    private String steamPublisherKey;

    @Value("${game.storefront.steam.app-id:}")
    private String steamAppId;

    @Value("${game.storefront.steam.sandbox:false}")
    private String steamSandbox;

    @Value("${game.storefront.steam.hmac-secret:}")
    private String steamHmacSecret;

    @PostConstruct
    public void apply() {
        set("STOREFRONT_GOOGLE_PACKAGE_NAME", googlePackage);
        set("STOREFRONT_GOOGLE_ACCESS_TOKEN", googleAccessToken);
        set("STOREFRONT_GOOGLE_SERVICE_ACCOUNT_JSON", googleServiceAccountJson);
        set("STOREFRONT_GOOGLE_SECRET", googleSandboxSecret);
        set("STOREFRONT_APPLE_SHARED_SECRET", appleSharedSecret);
        set("STOREFRONT_APPLE_BUNDLE_ID", appleBundleId);
        set("STOREFRONT_APPLE_SECRET", appleSandboxSecret);
        set("STOREFRONT_STEAM_PUBLISHER_KEY", steamPublisherKey);
        set("STOREFRONT_STEAM_APP_ID", steamAppId);
        set("STOREFRONT_STEAM_SANDBOX", steamSandbox);
        set("STOREFRONT_STEAM_SECRET", steamHmacSecret);

        // Reload SPI so constructors re-read credentials
        StorefrontRegistry.clearForTests();
        StorefrontRegistry.registeredIds();

        System.out.println("[storefront] google_play live=" + isGoogleLive()
                + " app_store live=" + isAppleLive()
                + " steam live=" + isSteamLive());
    }

    private static void set(String key, String value) {
        if (value == null || value.isBlank()) return;
        System.setProperty(key, value.trim());
    }

    private static boolean isGoogleLive() {
        var s = StorefrontRegistry.get(GooglePlayStorefront.ID);
        return s instanceof GooglePlayStorefront g && g.isLive();
    }

    private static boolean isAppleLive() {
        var s = StorefrontRegistry.get(AppStoreStorefront.ID);
        return s instanceof AppStoreStorefront a && a.isLive();
    }

    private static boolean isSteamLive() {
        var s = StorefrontRegistry.get(SteamStorefront.ID);
        return s instanceof SteamStorefront st && st.isLive();
    }
}
