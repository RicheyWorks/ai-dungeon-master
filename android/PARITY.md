# Android ↔ web parity checklist (Goal G6)

Manual QA against a running engine (`http://10.0.2.2:8080` on emulator).

| Area | Web | Android | Notes |
|---|---|---|---|
| Guest session mint | ✅ | ✅ | `POST /v2/session` |
| Persist + restore JWT | ✅ | ✅ | SessionStore / SharedPreferences |
| `GET /v2/session/me` TTL sync | ✅ | ✅ | Updates `expiresAtEpochSeconds` on Sync |
| Near-expiry refresh (<2m) | ✅ | ✅ | `POST /v2/session/refresh` |
| Save meta / clear save | ✅ | ✅ | `GET/DELETE /v2/save` |
| Save / load / reset | ✅ | ✅ | session-scoped |
| Marketplace jobs list + resume | ✅ | ✅ | Mods tab |
| STOMP session topic only | ✅ | ✅ | `/topic/narrative/{sessionId}` |
| Scene prose | ✅ | ✅ | `quest.sceneDescription` |
| Story memory (title / recap) | ✅ | ✅ | `status.story` |
| Cinematic check card | ✅ | ✅ | `status.lastCheck` |
| Store sandbox unlock | ✅ | ✅ | Dev receipt → verify |
| Mods → Store unlock jump | ✅ | ✅ | locked pack SKU |

## How to verify (15 min)

1. Start jar; open Android app → **Sync** → session + JWT TTL shown.  
2. **Save** → meta shows bytes → **Load** → **Clear save**.  
3. Mods: install job appears under recent jobs; resume works.  
4. Play First Light → burn letter → Identity shows **Ash-Handed**.  
5. Enter combat → Attack → Cinematic check card.  
6. Mods: locked Black Hollows → Buy to unlock → Store sandbox `pack_the_hollows`.  

Play Billing full IAP remains a follow-up (G7 uses **dev** storefront vertical).
