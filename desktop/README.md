# Desktop play

Two ways to run AI Dungeon Master as a desktop experience:

| Path | What it is | Needs |
|---|---|---|
| **`launch.sh` / `launch.ps1`** | Starts the fat jar (if needed) and opens `/app/` in your browser | Java 17+, Maven only if the jar isn’t built yet |
| **`tauri/`** | Native WebView window aimed at the same SPA | Rust + platform WebView (optional) |

The game UI is the web SPA hosted by the engine at **`/app/`** (see PR “serve web SPA”).

## Quick play (recommended)

```bash
# from repo root
./desktop/launch.sh
```

Windows (PowerShell):

```powershell
.\desktop\launch.ps1
```

What it does:

1. If `http://127.0.0.1:8080` is already healthy → just open `/app/`
2. Else build the fat jar if missing (`mvn -pl service -am -DskipTests package`)
3. Start `service/target/ai-dungeon-master-service-1.0-SNAPSHOT.jar`
4. Wait until the API/SPA responds
5. Open the browser to the client
6. On Ctrl+C, stop the engine **only if this script started it**

### Environment knobs

| Variable | Default | Meaning |
|---|---|---|
| `DM_PORT` | `8080` | Engine port |
| `DM_HOST` | `127.0.0.1` | Engine host |
| `DM_APP_URL` | `http://$HOST:$PORT/app/` | Client URL to open |
| `DM_JAR` | `service/target/…-SNAPSHOT.jar` | Fat jar path |
| `DM_LOG` | `/tmp/…` or `%TEMP%` | Engine log file |

## Tauri window

See [`tauri/README.md`](tauri/README.md). Config defaults to loading the live
engine SPA so session + STOMP work without re-bundling the UI.

## Ship checklist (Steam-ish)

1. `./scripts/build-web.sh` so `/app/` is current in the jar  
2. `mvn -pl service -am -DskipTests package`  
3. Ship jar + `desktop/launch.sh` (or a Steam “launch option” that runs Java + URL)  
4. Later: Tauri sidecar that spawns the jar and owns the window  
