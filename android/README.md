# AI Dungeon Master — Android client

Compose (Material 3) client for the engine's v2 API, built directly on the
generated Kotlin SDK in [`../clients/kotlin`](../clients/kotlin) — the app
module includes the SDK sources via `sourceSets`, so regenerating the SDK
updates the app with no publishing step.

## What it does (v1)

**Game tab**
- Party status with HP/MP bars, levels, statuses, and fallen markers
- Current quest with outcome + progress, chaos level, combat flag
- "The story so far" — the engine's Chronicle memory (`recentEvents`)
- Available choices as buttons → `POST /v2/action`
- Free-text DM narration → `POST /v2/narrate`

**Mods tab**
- Installed content packs with runtime enable/disable switches
  (`GET /v2/catalog`, `POST /v2/catalog/packs/{id}/enable|disable`)
- Active narration provider + health, registered plugins per SPI

Server URL is configurable (defaults to `http://10.0.2.2:8080`, the emulator's
alias for the host machine).

## Build & run

Requirements: Android Studio (Koala or newer), JDK 17, an emulator or device
on API 26+.

1. Start the engine on your machine:
   `java -jar service/target/ai-dungeon-master-service-1.0-SNAPSHOT.jar`
2. Open the `android/` folder in Android Studio (File → Open). Studio will
   generate the Gradle wrapper on first sync if prompted — or run
   `gradle wrapper` in `android/` once.
3. Run the `app` configuration on an emulator. On a physical device, change
   the Server field to your machine's LAN address (e.g. `http://192.168.x.x:8080`).

The manifest allows cleartext HTTP for the dev server; front the engine with
TLS before shipping anything real.

## Layout

```
app/src/main/java/com/xai/dungeonmaster/android/
  MainActivity.kt   entry point, dark Material 3 theme
  GameViewModel.kt  StateFlow bridge over the synchronous SDK (Dispatchers.IO)
  GameApp.kt        tab shell + the Game screen
  ModsScreen.kt     catalog browser with pack enable/disable toggles
```

The generated SDK is synchronous (`jvm-okhttp4`); the ViewModel wraps every
call in `withContext(Dispatchers.IO)` and folds results/errors into one
`UiState`. Version pins live in `build.gradle.kts` (AGP 8.5, Kotlin 2.0,
Compose BOM 2024.06) — bump them freely, nothing here is version-sensitive.

## Not yet wired

- WebSocket narration stream (`/topic/narrative` via STOMP) — the REST
  `narrate` round-trip is used instead for v1
- Sessions/JWT (`POST /v2/session`) and entitlements
- Pack upload from the device (`POST /v2/catalog/packs`) — use the web
  mod browser; also requires regenerating the Kotlin SDK against the
  latest spec so `uploadPackV2` exists
