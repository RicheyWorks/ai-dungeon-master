# AI Dungeon Master — Web client

Browser SPA with feature parity to the Android and iOS shells, built on the
generated TypeScript SDK (`../clients/typescript`). Also staged into the engine
fat jar at `/app/`.

## Features

| Tab | Capabilities |
|---|---|
| **Game** | Session, party/quest/choices, save/load/reset, live STOMP narrate, keys **1–9** for choices, **Ctrl/⌘+Enter** to narrate |

| **Mods** | Marketplace list/search/async install, catalog, enable/disable, zip upload |
| **Store** | Entitlements, sandbox receipts, Steam order verify |
| **System** | Readiness + lean health probes (auto-refresh) |

- Guest session via `createSessionV2` + Bearer on every `/v2/*` call
- Session restore from `localStorage` (validated with `/v2/session/me`)
- Live narration over native WebSocket `/ws-stomp`
- Marketplace jobs via generated SDK (list/poll/cancel); async start edge-decoded
- Dark editorial theme (Cinzel + Source Sans, cool steel accent)

## Run

### Hosted by the engine (recommended)

```bash
./scripts/build-web.sh
mvn -pl service -am spring-boot:run
# → http://localhost:8080/app/
#    root / and /play also redirect there
```

### Standalone Vite (hot reload)

```bash
# terminal 1 — engine on :8080
# terminal 2
cd web && npm install && npm run dev
# → http://localhost:5173  (proxies /v2 + /ws-stomp → :8080)
```

Leave **Server** empty to use same origin (proxy or engine host).

```bash
VITE_BASE=/app/ npm run build   # same as scripts/build-web.sh
```

## Layout

```
web/
  package.json
  vite.config.ts          proxy /v2 + /ws-stomp in dev
  index.html
  src/
    App.tsx               tabs + session chrome
    api.ts                wrappers over @ai-dungeon-master/client
    sessionStore.ts       localStorage session + base URL
    stomp.ts              STOMP 1.2 over WebSocket
    devReceipts.ts        DevStorefront-compatible HMAC
    steamPurchase.ts      Steam order helpers
    styles.css            design tokens + chrome
```

## Desktop (Tauri)

This SPA is framework-light so a Tauri 2 shell can load `web/dist` as
`frontendDist`. Scaffold when ready:

```bash
npm create tauri-app@latest desktop -- --template vanilla-ts
# point tauri.conf frontendDist at ../web/dist
```

## Not yet wired

- Tauri / Steam packaging in this package alone (see `desktop/`)
- StoreKit / Play Billing (dev receipts cover the loop)
