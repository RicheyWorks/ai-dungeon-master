package com.xai.dungeonmaster.plugin;

/**
 * Wrapper around a single storefront SDK (Steamworks, Google Play Games,
 * Apple Game Center). The engine never imports vendor SDKs directly — each
 * storefront ships as a plugin that implements this interface.
 *
 * Each client binary loads exactly one StorefrontIntegration (the one that
 * matches the store it shipped through). The server has a thin counterpart
 * that validates receipts and reconciles entitlements across stores.
 */
public interface StorefrontIntegration extends Plugin {

    /**
     * Information about the currently signed-in storefront user.
     */
    Identity currentIdentity();

    /**
     * Unlock an achievement by its storefront-specific id.
     */
    void unlockAchievement(String achievementId);

    /**
     * Submit a score to a leaderboard.
     */
    void submitLeaderboard(String boardId, long score);

    /**
     * Open (or create) a cloud-save slot. May return a no-op handle if the
     * storefront cloud-save is unavailable.
     */
    CloudSaveHandle openCloudSave(String slot);

    /**
     * Begin an in-app-purchase flow for a SKU defined in the storefront catalog.
     * Returns a handle the caller polls for completion.
     */
    PurchaseFlow startPurchase(String productId);

    /**
     * Verify a purchase receipt server-side. Receipts received on the client
     * are forwarded to the server, which calls this on the matching plugin to
     * confirm with the store before granting the entitlement.
     */
    boolean verifyReceipt(String receipt);

    /** Stable storefront user identity. */
    final class Identity {
        public final String storefrontUserId;
        public final String displayName;
        public final boolean signedIn;

        public Identity(String storefrontUserId, String displayName, boolean signedIn) {
            this.storefrontUserId = storefrontUserId;
            this.displayName = displayName;
            this.signedIn = signedIn;
        }
    }

    /** Handle for reading / writing a cloud-save slot. */
    interface CloudSaveHandle {
        byte[] read();
        void write(byte[] data);
        boolean isAvailable();
    }

    /** Handle for an in-flight purchase. */
    interface PurchaseFlow {
        boolean isComplete();
        boolean wasSuccessful();
        String receipt();
    }
}
