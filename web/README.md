# AI Dungeon Master — Web / desktop client

Browser SPA with feature parity to the Android and iOS shells, built on the
generated TypeScript SDK (`../clients/typescript`). Suitable as:

- a local web UI next to the engine
- the front-end for a future **Tauri / Steam** desktop wrap

## Features

| Tab | Capabilities |
|---|---|
| **Game** | Session, party/quest/choices, save/load/reset, live STOMP narrate |
| **Mods** | Catalog, enable/disable, zip upload |
| **Store** | Entitlements, dev receipt purchases, manual verify |

- Guest session via `createSessionV2` + Bearer on every `/v2/*` call  
- **Session restore** from `localStorage` (validated with `/v2/session/me`)  
- Live narration over native WebSocket `/ws-stomp`  
- Dark “parchment & brass” theme (Cinzel + Source Sans)

## Run

```bash
# terminal 1 — engine
java -jar service/target/ai-dungeon-master-service-*.jar

# terminal 2 — web UI
cd web
npm install
npm run dev
```

Open http://localhost:5173 — Vite proxies `/v2` and `/ws-stomp` to
`http://127.0.0.1:8080`.

Leave **Server** empty to use the proxy (recommended in dev). Point it at
`http://host:8080` when opening a production build against a remote engine.

```bash
npm run build    # → web/dist
npm run preview  # serve dist
```

### Optional: ship as static files on the engine

Copy `web/dist/*` into `service/src/main/resources/static/app/` (or any static
path the Spring app already serves) so the UI is available at the same origin
as the API — no CORS, no proxy.

## Layout

```
web/
  package.json
  vite.config.ts          proxy /v2 + /ws-stomp in dev
  index.html
  src/
    App.tsx               tabs + session chrome
    api.ts                thin wrappers over @ai-dungeon-master/client
    sessionStore.ts       localStorage session + base URL
    stomp.ts              STOMP 1.2 over WebSocket
    devReceipts.ts        DevStorefront-compatible HMAC
    styles.css
```

## Desktop (Tauri) next step

This SPA is intentionally framework-light so a Tauri 2 shell can load `web/dist`
as its `frontendDist` with almost no glue. Scaffold when ready:

```bash
npm create tauri-app@latest desktop -- --template vanilla-ts
# point tauri.conf frontendDist at ../web/dist
```

## Not yet wired

- Tauri / Steam packaging  
- StoreKit / Play Billing (dev receipts cover the loop)  
- Pack upload progress UI  
