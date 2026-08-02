package com.xai.dungeonmaster.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Typed {@code game.rate-limit.*} settings shared by {@link RateLimitFilter},
 * {@link NarrationRateGuard}, and {@link ActionRateGuard}.
 */
@Component
public class RateLimitProperties {

    private final boolean enabled;
    private final boolean trustForwardedHeaders;
    private final int sessionPerMinute;
    private final int logoutPerMinute;
    private final int adminPerMinute;
    private final int installPerMinute;
    private final int narratePerMinute;
    private final int actionPerMinute;
    private final int savePerMinute;
    private final int metricsPerMinute;
    private final int verifyPerMinute;
    private final int defaultPerMinute;

    public RateLimitProperties(
            @Value("${game.rate-limit.enabled:true}") boolean enabled,
            @Value("${game.rate-limit.trust-forwarded-headers:false}") boolean trustForwardedHeaders,
            @Value("${game.rate-limit.session-per-minute:30}") int sessionPerMinute,
            @Value("${game.rate-limit.logout-per-minute:20}") int logoutPerMinute,
            @Value("${game.rate-limit.admin-per-minute:30}") int adminPerMinute,
            @Value("${game.rate-limit.install-per-minute:15}") int installPerMinute,
            @Value("${game.rate-limit.narrate-per-minute:20}") int narratePerMinute,
            @Value("${game.rate-limit.action-per-minute:60}") int actionPerMinute,
            @Value("${game.rate-limit.save-per-minute:30}") int savePerMinute,
            @Value("${game.rate-limit.metrics-per-minute:120}") int metricsPerMinute,
            @Value("${game.rate-limit.verify-per-minute:60}") int verifyPerMinute,
            @Value("${game.rate-limit.default-per-minute:120}") int defaultPerMinute) {
        this.enabled = enabled;
        this.trustForwardedHeaders = trustForwardedHeaders;
        this.sessionPerMinute = atLeastOne(sessionPerMinute);
        this.logoutPerMinute = atLeastOne(logoutPerMinute);
        this.adminPerMinute = atLeastOne(adminPerMinute);
        this.installPerMinute = atLeastOne(installPerMinute);
        this.narratePerMinute = atLeastOne(narratePerMinute);
        this.actionPerMinute = atLeastOne(actionPerMinute);
        this.savePerMinute = atLeastOne(savePerMinute);
        this.metricsPerMinute = atLeastOne(metricsPerMinute);
        this.verifyPerMinute = atLeastOne(verifyPerMinute);
        this.defaultPerMinute = atLeastOne(defaultPerMinute);
    }

    /** Builder for unit tests (defaults match dev {@code application.properties}). */
    public static Builder builder() {
        return new Builder();
    }

    public boolean enabled() { return enabled; }
    public boolean trustForwardedHeaders() { return trustForwardedHeaders; }
    public int sessionPerMinute() { return sessionPerMinute; }
    public int logoutPerMinute() { return logoutPerMinute; }
    public int adminPerMinute() { return adminPerMinute; }
    public int installPerMinute() { return installPerMinute; }
    public int narratePerMinute() { return narratePerMinute; }
    public int actionPerMinute() { return actionPerMinute; }
    public int savePerMinute() { return savePerMinute; }
    public int metricsPerMinute() { return metricsPerMinute; }
    public int verifyPerMinute() { return verifyPerMinute; }
    public int defaultPerMinute() { return defaultPerMinute; }

    private static int atLeastOne(int n) {
        return Math.max(1, n);
    }

    public static final class Builder {
        private boolean enabled = true;
        private boolean trustForwarded = false;
        private int session = 30;
        private int logout = 20;
        private int admin = 30;
        private int install = 15;
        private int narrate = 20;
        private int action = 60;
        private int save = 30;
        private int metrics = 120;
        private int verify = 60;
        private int defaultLimit = 120;

        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder trustForwardedHeaders(boolean v) { this.trustForwarded = v; return this; }
        public Builder sessionPerMinute(int v) { this.session = v; return this; }
        public Builder logoutPerMinute(int v) { this.logout = v; return this; }
        public Builder adminPerMinute(int v) { this.admin = v; return this; }
        public Builder installPerMinute(int v) { this.install = v; return this; }
        public Builder narratePerMinute(int v) { this.narrate = v; return this; }
        public Builder actionPerMinute(int v) { this.action = v; return this; }
        public Builder savePerMinute(int v) { this.save = v; return this; }
        public Builder metricsPerMinute(int v) { this.metrics = v; return this; }
        public Builder verifyPerMinute(int v) { this.verify = v; return this; }
        public Builder defaultPerMinute(int v) { this.defaultLimit = v; return this; }

        /** Set every per-minute bucket to the same value (handy for isolation tests). */
        public Builder allBuckets(int v) {
            this.session = v;
            this.logout = v;
            this.admin = v;
            this.install = v;
            this.narrate = v;
            this.action = v;
            this.save = v;
            this.metrics = v;
            this.verify = v;
            this.defaultLimit = v;
            return this;
        }

        public RateLimitProperties build() {
            return new RateLimitProperties(
                    enabled, trustForwarded, session, logout, admin, install, narrate, action, save,
                    metrics, verify, defaultLimit);
        }
    }
}
