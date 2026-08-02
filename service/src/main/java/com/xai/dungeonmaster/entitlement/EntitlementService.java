package com.xai.dungeonmaster.entitlement;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;
import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import com.xai.dungeonmaster.service.PackAutoEnabler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
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
 *
 * Optional {@link PackAutoEnabler} turns on content packs gated by the granted SKU.
 */
@Service
public class EntitlementService {

    private final EntitlementStore store;
    private final ReceiptLedger ledger;
    private final boolean replayProtection;
    private final PackAutoEnabler packAutoEnabler;

    /** Convenience constructor for tests/embedders (in-memory store + ledger). */
    public EntitlementService() {
        this(new InMemoryEntitlementStore(), new MemoryReceiptLedger(), true, null);
    }

    public EntitlementService(EntitlementStore store) {
        this(store, new MemoryReceiptLedger(), true, null);
    }

    public EntitlementService(EntitlementStore store, ReceiptLedger ledger) {
        this(store, ledger, true, null);
    }

    public EntitlementService(EntitlementStore store, ReceiptLedger ledger, boolean replayProtection) {
        this(store, ledger, replayProtection, null);
    }

    public EntitlementService(EntitlementStore store, ReceiptLedger ledger, PackAutoEnabler packAutoEnabler) {
        this(store, ledger, true, packAutoEnabler);
    }

    @Autowired
    public EntitlementService(
            EntitlementStore store,
            ReceiptLedger ledger,
            @Value("${game.auth.receipt-ledger.enabled:true}") boolean replayProtection,
            @Autowired(required = false) PackAutoEnabler packAutoEnabler) {
        this.store = (store != null) ? store : new InMemoryEntitlementStore();
        this.ledger = (ledger != null) ? ledger : new MemoryReceiptLedger();
        this.replayProtection = replayProtection;
        this.packAutoEnabler = packAutoEnabler;
    }

    /** Verify a receipt through the named storefront and, if valid, grant the product. */
    public Grant verifyAndGrant(String sessionId, String storefrontId, String productId, String receipt) {
        if (sessionId == null || sessionId.isBlank()) {
            return new Grant(false, productId, storefrontId, "no session", List.of());
        }
        if (productId == null || productId.isBlank()) {
            return new Grant(false, productId, storefrontId, "productId is required", List.of());
        }
        StorefrontIntegration storefront = (storefrontId == null || storefrontId.isBlank())
                ? StorefrontRegistry.getActive()
                : StorefrontRegistry.get(storefrontId);
        if (storefront == null) {
            return new Grant(false, productId, storefrontId, "unknown storefront '" + storefrontId + "'", List.of());
        }
        String sfId = storefront.id();
        if (receipt == null || receipt.isBlank()) {
            return new Grant(false, productId, sfId, "receipt is required", List.of());
        }

        String fp = ReceiptLedger.fingerprint(sfId, productId, receipt);
        if (replayProtection) {
            var prior = ledger.find(fp);
            if (prior.isPresent()) {
                ReceiptLedger.RedeemRecord r = prior.get();
                // Idempotent re-submit for the same session + product is OK.
                if (sessionId.equals(r.sessionId()) && productId.equals(r.productId())) {
                    store.grant(sessionId, productId);
                    List<String> packs = autoEnable(sessionId, productId);
                    return new Grant(true, productId, sfId, "already redeemed (idempotent)", packs);
                }
                return new Grant(false, productId, sfId, "receipt already redeemed", List.of());
            }
        }

        if (!storefront.verifyReceipt(receipt)) {
            return new Grant(false, productId, sfId, "receipt failed verification", List.of());
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
        List<String> packs = autoEnable(sessionId, productId);
        String reason = packs.isEmpty()
                ? "granted"
                : "granted; enabled packs " + packs;
        return new Grant(true, productId, sfId, reason, packs);
    }

    private List<String> autoEnable(String sessionId, String productId) {
        if (packAutoEnabler == null) return List.of();
        try {
            return packAutoEnabler.enableForGrant(sessionId, productId);
        } catch (Exception e) {
            System.err.println("[entitlements] pack auto-enable failed: " + e.getMessage());
            return List.of();
        }
    }

    /** Products the session currently owns. */
    public Set<String> entitlements(String sessionId) {
        return store.products(sessionId);
    }

    public boolean isEntitled(String sessionId, String productId) {
        return store.owns(sessionId, productId);
    }

    /** Outcome of a verify-and-grant attempt. */
    public record Grant(
            boolean granted,
            String productId,
            String storefront,
            String reason,
            List<String> enabledPacks) {
        public Grant(boolean granted, String productId, String storefront, String reason) {
            this(granted, productId, storefront, reason, List.of());
        }
    }
}
