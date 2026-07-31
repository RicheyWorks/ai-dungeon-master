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

### Hosted by the engine (recommended)

```bash
# stage the SPA into service/src/main/resources/static/app/
./scripts/build-web.sh

# start the engine (any usual way)
mvn -pl service -am spring-boot:run
# → open http://localhost:8080/app/
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

### Optional: ship as static files on the engine

`scripts/build-web.sh` already copies `web/dist/*` into
`service/src/main/resources/static/app/`. Re-run after UI changes before
packaging the fat jar so players always get the latest shell.

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
