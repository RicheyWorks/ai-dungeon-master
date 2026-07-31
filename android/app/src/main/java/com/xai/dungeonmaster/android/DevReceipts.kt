package com.xai.dungeonmaster.android

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Client-side mint of [DevStorefront](core) test receipts so the Android
 * "Buy (dev)" button can exercise the full verify → grant loop without a real
 * Play Billing purchase.
 *
 * Format: `base64url(productId).base64url(HMAC_SHA256(secret, productId))`
 * Default secret matches the server's insecure local default.
 */
object DevReceipts {

    const val DEFAULT_SECRET = "dev-storefront-insecure-secret-change-me"
    const val STOREFRONT_ID = "dev"

    fun sign(productId: String, secret: String = DEFAULT_SECRET): String {
        val p = productId.toByteArray(Charsets.UTF_8)
        val body = b64(p)
        val sig = b64(hmac(secret.toByteArray(Charsets.UTF_8), p))
        return "$body.$sig"
    }

    private fun hmac(secret: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}
