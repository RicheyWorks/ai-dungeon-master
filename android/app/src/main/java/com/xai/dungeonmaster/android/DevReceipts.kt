package com.xai.dungeonmaster.android

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Client-side mint of sandbox receipts for server storefront plugins.
 *
 * HMAC format (shared by `dev`, and sandbox modes of `google_play` / `app_store`):
 * `base64url(productId).base64url(HMAC_SHA256(secret, productId))`
 *
 * Defaults match the engine's insecure local secrets so the Store tab works
 * out of the box. For Google Play / App Store, the payload is wrapped as the
 * JSON shape live clients will eventually send (purchaseToken / receiptData).
 */
object DevReceipts {

    const val STOREFRONT_DEV = "dev"
    const val STOREFRONT_GOOGLE_PLAY = "google_play"
    const val STOREFRONT_APP_STORE = "app_store"

    /** @deprecated use [STOREFRONT_DEV] */
    const val STOREFRONT_ID = STOREFRONT_DEV

    const val SECRET_DEV = "dev-storefront-insecure-secret-change-me"
    const val SECRET_GOOGLE = "google-play-sandbox-insecure-secret"
    const val SECRET_APPLE = "app-store-sandbox-insecure-secret"

    /** Default package / bundle used in JSON wrappers (override in real builds). */
    const val DEFAULT_PACKAGE_NAME = "com.xai.dungeonmaster"

    /** @deprecated use [SECRET_DEV] */
    const val DEFAULT_SECRET = SECRET_DEV

    val knownStorefronts: List<String> = listOf(
        STOREFRONT_DEV,
        STOREFRONT_GOOGLE_PLAY,
        STOREFRONT_APP_STORE,
    )

    fun secretFor(storefront: String): String = when (storefront.lowercase()) {
        STOREFRONT_GOOGLE_PLAY -> SECRET_GOOGLE
        STOREFRONT_APP_STORE -> SECRET_APPLE
        else -> SECRET_DEV
    }

    fun sign(productId: String, secret: String = SECRET_DEV): String {
        val p = productId.toByteArray(Charsets.UTF_8)
        val body = b64(p)
        val sig = b64(hmac(secret.toByteArray(Charsets.UTF_8), p))
        return "$body.$sig"
    }

    /**
     * Build a receipt + storefront id pair ready for `POST /v2/entitlements/verify`.
     * Google Play and App Store use JSON envelopes so the payload matches live clients.
     */
    fun mint(storefront: String, productId: String, packageName: String = DEFAULT_PACKAGE_NAME): Minted {
        val id = storefront.ifBlank { STOREFRONT_DEV }.lowercase()
        val hmac = sign(productId, secretFor(id))
        val receipt = when (id) {
            STOREFRONT_GOOGLE_PLAY ->
                """{"packageName":${json(packageName)},"productId":${json(productId)},"purchaseToken":${json(hmac)}}"""
            STOREFRONT_APP_STORE ->
                """{"receiptData":${json(hmac)},"productId":${json(productId)}}"""
            else -> hmac
        }
        return Minted(storefront = id, productId = productId, receipt = receipt)
    }

    data class Minted(val storefront: String, val productId: String, val receipt: String)

    private fun json(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun hmac(secret: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
