package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;
import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Server-side purchase-receipt validation and entitlement tracking — the thin
 * counterpart to the client {@link StorefrontIntegration} plugins. A client
 * forwards a receipt; this service routes it to the matching storefront's
 * {@link StorefrontIntegration#verifyReceipt}, and on success records the
 * product against the player's session via a pluggable {@link EntitlementStore}.
 *
 * Default store is in-memory; file-backed (and locked) stores survive restarts
 * and multi-process deployments that share a volume — mirroring the session store.
 *
 * Receipts are one-time: a successful redeem is recorded in {@link ReceiptLedger}
 * so the same store payload cannot grant another session (replay protection).
 */
@Service
public class EntitlementService {

    private final EntitlementStore store;
    private final ReceiptLedger ledger;
    private final boolean replayProtection;

    /** Convenience constructor for tests/embedders (in-memory store + ledger). */
    public EntitlementService() {
        this(new InMemoryEntitlementStore(), new MemoryReceiptLedger(), true);
    }

    public EntitlementService(EntitlementStore store) {
        this(store, new MemoryReceiptLedger(), true);
    }

    public EntitlementService(EntitlementStore store, ReceiptLedger ledger) {
        this(store, ledger, true);
    }

    @Autowired
    public EntitlementService(
            EntitlementStore store,
            ReceiptLedger ledger,
            @Value("${game.auth.receipt-ledger.enabled:true}") boolean replayProtection) {
        this.store = (store != null) ? store : new InMemoryEntitlementStore();
        this.ledger = (ledger != null) ? ledger : new MemoryReceiptLedger();
        this.replayProtection = replayProtection;
    }

    /** Verify a receipt through the named storefront and, if valid, grant the product. */
    public Grant verifyAndGrant(String sessionId, String storefrontId, String productId, String receipt) {
        if (sessionId == null || sessionId.isBlank()) {
            return new Grant(false, productId, storefrontId, "no session");
        }
        if (productId == null || productId.isBlank()) {
            return new Grant(false, productId, storefrontId, "productId is required");
        }
        StorefrontIntegration storefront = (storefrontId == null || storefrontId.isBlank())
                ? StorefrontRegistry.getActive()
                : StorefrontRegistry.get(storefrontId);
        if (storefront == null) {
            return new Grant(false, productId, storefrontId, "unknown storefront '" + storefrontId + "'");
        }
        String sfId = storefront.id();
        if (receipt == null || receipt.isBlank()) {
            return new Grant(false, productId, sfId, "receipt is required");
        }

        String fp = ReceiptLedger.fingerprint(sfId, productId, receipt);
        if (replayProtection) {
            var prior = ledger.find(fp);
            if (prior.isPresent()) {
                ReceiptLedger.RedeemRecord r = prior.get();
                // Idempotent re-submit for the same session + product is OK.
                if (sessionId.equals(r.sessionId()) && productId.equals(r.productId())) {
                    store.grant(sessionId, productId);
                    return new Grant(true, productId, sfId, "already redeemed (idempotent)");
                }
                return new Grant(false, productId, sfId, "receipt already redeemed");
            }
        }

        if (!storefront.verifyReceipt(receipt)) {
            return new Grant(false, productId, sfId, "receipt failed verification");
        }

        store.grant(sessionId, productId);
        if (replayProtection) {
            ledger.record(new ReceiptLedger.RedeemRecord(
                    fp, sessionId, productId, sfId, System.currentTimeMillis()));
        }
        try {
            storefront.afterGrant(productId, receipt);
        } catch (Exception e) {
            System.err.println("[entitlements] afterGrant failed for " + sfId
                    + ": " + e.getMessage());
        }
        return new Grant(true, productId, sfId, "granted");
    }

    /** Products the session currently owns. */
    public Set<String> entitlements(String sessionId) {
        return store.products(sessionId);
    }

    public boolean isEntitled(String sessionId, String productId) {
        return store.owns(sessionId, productId);
    }

    /** Outcome of a verify-and-grant attempt. */
    public record Grant(boolean granted, String productId, String storefront, String reason) {}
}
