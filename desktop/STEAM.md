# Steam desktop purchase path

The engine validates Steam microtransactions via the **Partner Web API**
(`ISteamMicroTxn` / `ISteamMicroTxnSandbox`) when:

| Property | Env |
|---|---|
| `game.storefront.steam.publisher-key` | `STOREFRONT_STEAM_PUBLISHER_KEY` |
| `game.storefront.steam.app-id` | `STOREFRONT_STEAM_APP_ID` |
| `game.storefront.steam.sandbox` | `STOREFRONT_STEAM_SANDBOX` |

## Client receipt shape

```json
{"orderId":"1234567890","steamId":"7656119…","productId":"sku_gold"}
```

Posted to `POST /v2/entitlements/verify` with `storefront: "steam"`.

## Flow (desktop / Steamworks overlay)

1. **InitTxn** (your Steamworks backend or Steam Partner) creates an order for the
   logged-in Steam user and SKU.
2. Steam overlay / Steamworks client completes authorization.
3. Desktop client (or web SPA launched via `desktop/launch.sh`) posts the
   `{orderId, steamId, productId}` receipt to the engine.
4. Engine **QueryTxn** verifies the order, grants the entitlement, then
   **FinalizeTxn** settles the microtransaction (`afterGrant`).

## Local / CI without a publisher key

Use the web Store tab **Sandbox purchase** with storefront `steam` — HMAC
receipts match `STOREFRONT_STEAM_SECRET` (default insecure local secret).

## Ship with `desktop/launch.sh`

```bash
export STOREFRONT_STEAM_PUBLISHER_KEY=…
export STOREFRONT_STEAM_APP_ID=…
export STOREFRONT_STEAM_SANDBOX=true   # partner sandbox
./desktop/launch.sh
# open Store → paste orderId or use sandbox mint
```

Tauri / Steam “launch option” can set the same env vars before spawning the jar.
