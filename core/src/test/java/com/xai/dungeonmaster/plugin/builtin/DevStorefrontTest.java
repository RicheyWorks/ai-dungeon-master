package com.xai.dungeonmaster.plugin.builtin;

import com.xai.dungeonmaster.plugin.StorefrontIntegration;
import com.xai.dungeonmaster.plugin.StorefrontRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** DevStorefront signs receipts its own verifyReceipt accepts, and rejects tampering. */
class DevStorefrontTest {

    @Test
    void signedReceiptVerifies() {
        DevStorefront store = new DevStorefront();
        String receipt = store.signReceipt("sku_gold_pack");
        assertTrue(store.verifyReceipt(receipt), "a freshly signed receipt should verify");
    }

    @Test
    void tamperedReceiptRejected() {
        DevStorefront store = new DevStorefront();
        String receipt = store.signReceipt("sku_gold_pack");
        String tampered = receipt.substring(0, receipt.length() - 2) + "XY";
        assertFalse(store.verifyReceipt(tampered), "a tampered signature must not verify");
        assertFalse(store.verifyReceipt("garbage"), "malformed receipts must not verify");
        assertFalse(store.verifyReceipt(null));
    }

    @Test
    void purchaseFlowProducesVerifiableReceipt() {
        DevStorefront store = new DevStorefront();
        StorefrontIntegration.PurchaseFlow flow = store.startPurchase("sku_starter");
        assertTrue(flow.isComplete() && flow.wasSuccessful());
        assertTrue(store.verifyReceipt(flow.receipt()), "purchase receipt should verify");
    }

    @Test
    void registeredViaServiceLoaderAlongsideNoOp() {
        StorefrontRegistry.clearForTests();
        assertTrue(StorefrontRegistry.isRegistered("dev"), "dev storefront should ServiceLoader-register");
        assertTrue(StorefrontRegistry.isRegistered("none"), "no-op storefront should remain registered");
        assertFalse(new NoOpStorefront().verifyReceipt("anything"), "offline store verifies nothing");
        StorefrontRegistry.clearForTests();
    }
}
