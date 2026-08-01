# Storefronts (receipt verification)

Server-side plugins implementing `StorefrontIntegration`. Clients POST receipts
to `POST /v2/entitlements/verify` with a `storefront` id; the matching plugin
validates, then entitlements are granted.

| Id | Class | Mode |
|---|---|---|
| `none` | `NoOpStorefront` | Always rejects |
| `dev` | `DevStorefront` | HMAC test receipts |
| `google_play` | `GooglePlayStorefront` | Live Android Publisher **or** sandbox HMAC |
| `app_store` | `AppStoreStorefront` | Live Apple `verifyReceipt` **or** sandbox HMAC |

## Developer (`dev`)

```text
STOREFRONT_DEV_SECRET=…   # default insecure string for local only
```

Receipt: `base64url(productId).base64url(HMAC_SHA256(secret, productId))`  
Android/iOS/web clients already mint these for the Store tab.

## Google Play (`google_play`)

### Sandbox (default)

```text
STOREFRONT_GOOGLE_SECRET=…   # HMAC secret (default insecure)
```

Accepts the same HMAC receipt shape as `dev`, or JSON:

```json
{
  "packageName": "com.example.dm",
  "productId": "sku_gold",
  "purchaseToken": "<hmac-receipt-for-productId>"
}
```

### Live

```text
STOREFRONT_GOOGLE_PACKAGE_NAME=com.example.dm
STOREFRONT_GOOGLE_ACCESS_TOKEN=<oauth-access-token>
```

Calls Android Publisher
`GET /androidpublisher/v3/applications/{package}/purchases/products/{sku}/tokens/{token}`.

Supply a short-lived access token from your deploy environment (service account
→ OAuth). The engine does not embed a service-account private key.

Receipt JSON from the Android client:

```json
{
  "packageName": "com.example.dm",
  "productId": "sku_gold",
  "purchaseToken": "<play-billing-purchase-token>"
}
```

## App Store (`app_store`)

### Sandbox (default)

```text
STOREFRONT_APPLE_SECRET=…   # HMAC secret (default insecure)
```

### Live

```text
STOREFRONT_APPLE_SHARED_SECRET=<app-specific-shared-secret>
STOREFRONT_APPLE_BUNDLE_ID=com.example.dm   # optional bundle check
```

Posts to `https://buy.itunes.apple.com/verifyReceipt`, retries sandbox on
status `21007`.

Receipt: raw base64 App Store receipt, or:

```json
{
  "receiptData": "<base64-app-receipt>",
  "productId": "sku_gold"
}
```

## Client wiring

| Client | How to buy |
|---|---|
| Web / Android Store tab | `storefront=dev` + local HMAC mint |
| Android Play Billing | Obtain purchase token → `storefront=google_play` + JSON body |
| iOS StoreKit | App receipt → `storefront=app_store` |

## Security

- Never ship production builds with default sandbox secrets  
- Prefer live mode in prod; keep sandbox for CI only  
- Rotate `STOREFRONT_*` secrets with the same cadence as JWT secrets  
