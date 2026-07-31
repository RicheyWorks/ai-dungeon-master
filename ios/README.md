# AI Dungeon Master — iOS / macOS client

SwiftUI client for the engine's v2 API, built on the generated Swift SDK in
[`../clients/swift`](../clients/swift). Feature parity with the Android v1 shell.

## What it does

**Session identity**
- Auto-mints a guest session on first contact (`POST /v2/session`)
- Attaches `Authorization: Bearer <jwt>` via `AIDungeonMasterClientAPI.customHeaders`
- **Persists** session + server URL in `UserDefaults`; relaunch restores the JWT
  (validated via `GET /v2/session/me`) so the per-session world continues
- UI shows display name + short session id; **New session** re-mints

**Live narration (STOMP)**
- Native WebSocket to `ws://…/ws-stomp`
- CONNECT with Bearer; subscribe `/topic/narrative` + `/topic/narrative/{sessionId}`
- **Stream narrate** sends `/app/narrate` and renders `narrative_chunk` frames live

**Game tab**
- Party / quest / chronicle / choices
- Save / Load / Reset (`saveGameV2` / `loadGameV2` / `resetGameV2`)
- Free-text DM narration (STOMP or REST fallback)

**Mods tab**
- Catalog + pack enable/disable
- Upload pack zip (document picker)

**Store tab**
- List entitlements
- Dev receipt purchases (HMAC matching server `DevStorefront`)
- Manual receipt verify

## Open in Xcode

Requirements: Xcode 15+, iOS 16 / macOS 13 deployment target.

1. Start the engine: `java -jar service/target/ai-dungeon-master-service-*.jar`
2. Open `ios/Package.swift` in Xcode (or add the package path as a local dependency).
3. Create a new **iOS App** (or macOS App) target in a workspace that depends on
   the `AIDungeonMasterApp` library product.
4. Set the app entry to:

```swift
import SwiftUI
import AIDungeonMasterApp

@main
struct HostApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

5. For the iOS Simulator, the default server URL is `http://127.0.0.1:8080`.
   On a physical device, set Server to your machine's LAN address.

Alternatively, use [XcodeGen](https://github.com/yonaskolb/XcodeGen) / an
existing workspace — the Swift sources under `Sources/AIDungeonMasterApp` are
self-contained once the `AIDungeonMasterClient` package is resolved.

### ATS / cleartext HTTP

Local dev uses HTTP. Add to the host app's `Info.plist` (iOS):

```xml
<key>NSAppTransportSecurity</key>
<dict>
  <key>NSAllowsLocalNetworking</key>
  <true/>
</dict>
```

## Layout

```
ios/
  Package.swift
  README.md
  Sources/AIDungeonMasterApp/
    AIDungeonMasterApp.swift   public root helpers
    ContentView.swift          tab shell + server bar
    GameViewModel.swift        session, REST, STOMP, store, mods
    SessionInfo.swift
    SessionStore.swift         UserDefaults persistence
    Networking/
      StompClient.swift        STOMP 1.2 over URLSessionWebSocketTask
      DevReceipts.swift        DevStorefront-compatible HMAC mint
    Views/
      GameTab.swift
      ModsTab.swift
      StoreTab.swift
```

## Not yet wired

- Real StoreKit 2 purchases (dev storefront covers the verify loop)
- Encrypted keychain storage for JWT (UserDefaults is fine for guest tokens)
- Push notifications
