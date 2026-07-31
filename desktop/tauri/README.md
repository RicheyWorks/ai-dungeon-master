# Tauri shell (optional desktop wrap)

Scaffold for a native window that loads the engine’s web SPA at
`http://127.0.0.1:8080/app/` (or packs a static copy of `web/dist`).

## Why this folder is thin

Full `tauri build` needs platform WebView libraries (e.g. WebKitGTK on Linux,
WebView2 on Windows). This repo’s CI/sandbox may not ship them, so we keep a
**config-first** skeleton here and document the host requirements.

## Prerequisites

| OS | Needs |
|---|---|
| Linux | `webkit2gtk`, `libgtk-3`, `librsvg`, Rust stable |
| macOS | Xcode CLT, Rust stable |
| Windows | WebView2 runtime, Rust stable, MSVC |

```bash
# once on a machine with WebView deps:
cargo install tauri-cli --version "^2"
```

## Dev loop

```bash
# terminal 1 — engine with SPA
./scripts/build-web.sh   # if UI changed
mvn -pl service -am spring-boot:run

# terminal 2 — Tauri window pointing at the engine
cd desktop/tauri
cargo tauri dev
```

`tauri.conf.json` defaults `devUrl` / `frontendDist` to the live engine URL so
you always get session, STOMP, and mods against a real backend.

## Bundle static UI instead of a live URL

1. `VITE_BASE=./ npm run build` in `web/` (or keep `/app/` and open that path).
2. Point `build.frontendDist` at `../../web/dist`.
3. Still run the Java engine for API + WebSocket (Tauri does not replace the server).

For a single-process “Steam-like” ship later: have Tauri spawn the fat jar as a
sidecar (see Tauri externalBin / shell plugin) and wait on `/v2/catalog` before
showing the window — same logic as `desktop/launch.sh`.

## Not a substitute for

- `desktop/launch.sh` / `launch.ps1` — zero-Rust “double-click play” today  
- Mobile clients under `android/` and `ios/`
