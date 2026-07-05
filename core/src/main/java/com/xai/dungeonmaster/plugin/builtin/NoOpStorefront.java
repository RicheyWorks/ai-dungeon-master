package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;

/**
 * The bundled offline storefront. Signs no one in, drops achievement and
 * leaderboard calls, exposes an unavailable cloud-save handle, and refuses to
 * validate receipts. This is the always-available fallback returned by
 * {@link com.xai.dungeonmaster.plugin.StorefrontRegistry#getActive()} when a
 * build ships without a real store integration (Steamworks, Play Games, Game
 * Center) — for example the pure-Java engine in tests and offline desktop runs.
 *
 * A worked example of the {@link StorefrontIntegration} contract: a real
 * storefront plugin implements the same interface backed by its vendor SDK.
 */
public final class NoOpStorefront implements StorefrontIntegration {

    /** Stable id of the offline no-op storefront. */
    public static final String ID = "none";

    private static final Identity ANONYMOUS = new Identity(null, "Guest", false);

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Offline (no storefront)"; }

    @Override
    public Identity currentIdentity() {
        return ANONYMOUS;
    }

    @Override
    public void unlockAchievement(String achievementId) {
        // No storefront to record against — intentionally a no-op.
    }

    @Override
    public void submitLeaderboard(String boardId, long score) {
        // No storefront to submit to — intentionally a no-op.
    }

    @Override
    public CloudSaveHandle openCloudSave(String slot) {
        return new UnavailableCloudSave();
    }

    @Override
    public PurchaseFlow startPurchase(String productId) {
        return new FailedPurchaseFlow();
    }

    @Override
    public boolean verifyReceipt(String receipt) {
        // Offline builds cannot confirm a purchase with any store.
        return false;
    }

    /** A cloud-save handle that reports itself unavailable and stores nothing. */
    private static final class UnavailableCloudSave implements CloudSaveHandle {
        @Override public byte[] read() { return new byte[0]; }
        @Override public void write(byte[] data) { /* discarded */ }
        @Override public boolean isAvailable() { return false; }
    }

    /** A purchase flow that completes immediately as unsuccessful. */
    private static final class FailedPurchaseFlow implements PurchaseFlow {
        @Override public boolean isComplete() { return true; }
        @Override public boolean wasSuccessful() { return false; }
        @Override public String receipt() { return null; }
    }
}
