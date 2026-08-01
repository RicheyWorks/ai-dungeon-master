package com.xai.dungeonmaster.android

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import org.json.JSONObject

/**
 * Play Billing wrapper → server `google_play` JSON receipt, then client-side
 * acknowledge (and optional consume) after a successful grant.
 */
class PlayBillingPurchaser(
    private val activity: Activity,
    private val onReceipt: (productId: String, receiptJson: String, purchaseToken: String) -> Unit,
    private val onError: (String) -> Unit,
    private val consumeAfterAck: Boolean = false,
) : PurchasesUpdatedListener {

    private var client: BillingClient? = null

    fun start() {
        if (client != null) return
        val c = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    onError("Play Billing setup: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // next purchase will re-connect
            }
        })
    }

    fun purchase(productId: String) {
        val c = client
        if (c == null || !c.isReady) {
            onError("Play Billing not ready — use sandbox or retry")
            start()
            return
        }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        c.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK || detailsList.isNullOrEmpty()) {
                onError("Product not found: $productId (${result.debugMessage})")
                return@queryProductDetailsAsync
            }
            launch(detailsList.first())
        }
    }

    private fun launch(details: ProductDetails) {
        val c = client ?: return
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val result = c.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onError("launchBillingFlow: ${result.debugMessage}")
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) {
            if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                onError("Purchase failed: ${result.debugMessage}")
            }
            return
        }
        for (p in purchases) {
            for (sku in p.products) {
                val json = JSONObject()
                    .put("packageName", p.packageName)
                    .put("productId", sku)
                    .put("purchaseToken", p.purchaseToken)
                    .toString()
                onReceipt(sku, json, p.purchaseToken)
            }
        }
    }

    /** Acknowledge (and optionally consume) after server grant succeeds. */
    fun settle(purchaseToken: String, onSettled: ((Boolean) -> Unit)? = null) {
        val c = client
        if (c == null || purchaseToken.isBlank()) {
            onSettled?.invoke(false)
            return
        }
        if (consumeAfterAck) {
            val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
            c.consumeAsync(params) { result, _ ->
                onSettled?.invoke(result.responseCode == BillingClient.BillingResponseCode.OK)
            }
            return
        }
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        c.acknowledgePurchase(params) { result ->
            onSettled?.invoke(result.responseCode == BillingClient.BillingResponseCode.OK)
        }
    }

    fun end() {
        client?.endConnection()
        client = null
    }
}
