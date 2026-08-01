package com.xai.dungeonmaster.android

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import org.json.JSONObject

/**
 * Thin Play Billing wrapper that produces the JSON receipt envelope
 * expected by server {@code google_play} storefront verification:
 * {@code {"packageName","productId","purchaseToken"}}.
 *
 * Falls back to caller-handled sandbox when Billing is unavailable (emulator
 * without Play Services, etc.).
 */
class PlayBillingPurchaser(
    private val activity: Activity,
    private val onReceipt: (productId: String, receiptJson: String) -> Unit,
    private val onError: (String) -> Unit,
) : PurchasesUpdatedListener {

    private var client: BillingClient? = null
    private var pendingSku: String? = null

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
        pendingSku = productId
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
                onReceipt(sku, json)
            }
        }
    }

    fun end() {
        client?.endConnection()
        client = null
    }
}
