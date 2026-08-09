# Production deploy checklist

## Launch checklist (go-live)

Ordered path from zero to verified multiplayer backend.

### 0. Pre-flight

- [ ] `cp deploy/.env.example deploy/.env`
- [ ] `./scripts/gen-secrets.sh >> deploy/.env`
- [ ] Set `GAME_CORS_ALLOWED_ORIGINS` (your public origin(s), no `*`)
- [ ] Set `DOMAIN` and place TLS PEMs under `deploy/certs/`
- [ ] `./scripts/verify-prod-env.sh deploy/.env`
- [ ] Confirm content packs present under `content-packs/` (image + compose mount)

### 1. Deploy

```bash
# Core multi-node + prod secrets + TLS
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  up --build -d

# Optional observability
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  -f deploy/docker-compose.metrics.yml \
  up -d
```

### 2. Smoke (required)

```bash
# Against the public/proxy URL
BASE_URL=https://$DOMAIN ADMIN_TOKEN=$GAME_ADMIN_TOKEN ./scripts/launch-smoke.sh

# Full green gate (tests + local engine + smoke) — no Docker required
./scripts/launch-check.sh
```

Play path covered by `launch-smoke.sh`:

1. `POST /v2/session` (mint)
2. `POST /v2/session/refresh` (optional — same id, fresh JWT)
3. `PATCH /v2/session` (optional — rename display name + fresh JWT)
2. `GET /v2/session/me`
3. `GET /v2/catalog` + optional pack enable
4. `GET /v2/status` → `POST /v2/action`
5. `POST /v2/narrate`
6. `POST /v2/save` → `POST /v2/load`
7. `GET /v2/entitlements`
8. Optional `GET /v2/admin/receipts` with admin token
9. `DELETE /v2/session` (logout) + confirm 401 after

### 3. Ops after go-live

| Task | How |
|---|---|
| Rotate JWT | set new `GAME_AUTH_JWT_SECRET`, rolling restart app1 then app2 (sessions invalidate) |
| Rotate admin token | set `GAME_ADMIN_TOKEN`, restart apps |
| Rate-limit spike (`DmRateLimitSpike`) | check Grafana rate-limit panels; temporarily raise `game.rate-limit.*-per-minute` or block abusive IP at nginx |
| Inspect receipts | `GET /v2/admin/receipts?limit=50` + `X-Admin-Token` |
| Inspect session packs | `GET /v2/admin/session-packs?sessionId=…` + `X-Admin-Token` |
| List sessions | `GET /v2/admin/sessions?limit=100` + `X-Admin-Token` |
| Security events | `GET /v2/admin/security-events?limit=50` + `X-Admin-Token` (process-local ring) |
| Admin audit | `GET /v2/admin/audit-events?limit=50` + `X-Admin-Token` (ops ring) |
| Narration provider | `GET /v2/admin/narration` · `POST /v2/admin/narration/provider?id=` + `X-Admin-Token` |
| Revoke session | `DELETE /v2/admin/sessions/{sessionId}` + `X-Admin-Token` |
| Purge idle | `POST /v2/admin/sessions/purge-idle?idleTtlSeconds=86400&evictEngines=true` + `X-Admin-Token` |
| Web SPA ops | System tab: admin/metrics tokens, sessions/receipts/packs/security+audit events, narration switch, purge idle, export diagnostics JSON |
| Rollback | redeploy previous image tag; keep Postgres + `saves` volumes |

### 4. Abuse surface (prod defaults)

| Control | Prod default |
|---|---|
| JSON body cap | 512 KiB |
| XFF trust | on (behind nginx only) |
| STOMP JWT | required when `game.auth.enabled` |
| Legacy `/api/game` | **410 disabled** |
| Marketplace download cap | 12 MiB |
| Rate-limit store | redis (shared) |

### 5. Client parity

Web (`web/src/api.ts`) uses the generated TS SDK for session/action/narrate/save/load/logout/catalog/entitlements
**and marketplace list/install/jobs**. Android and iOS ViewModels call the regenerated Kotlin/Swift
`V2Api`/`V2API` for marketplace list, job poll, and cancel (async start still decodes the 202 job
envelope until OpenAPI models a dual response). SDKs live under `clients/kotlin` and `clients/swift`.


---
## Fail-fast security guard

When `game.production=true` **or** Spring profile `prod` is active, the service
runs `ProductionSecurityGuard` at boot and **refuses to start** if:

| Check | Requirement |
|---|---|
| Auth | `game.auth.enabled=true` |
| JWT secret | set, ≥ 32 chars, not a known dev default |
| Session / entitlement / receipt / session-packs store | not `memory` |
| Rate-limit store | not `memory` (use `redis`) |
| Admin token | `game.admin.token` / `GAME_ADMIN_TOKEN` ≥ 24 chars |
| Token compare | SHA-256 digest compare (`SecretEquals`) — no length oracle |
| CORS | `game.cors.allowed-origins` explicit allow-list (no `*`) |
| JDBC password | not a known default when store is jdbc |
| Storefronts | strong sandbox secrets **or** live vendor credentials |

Local dev is unchanged (`game.production=false` by default).

Activate either way:

```bash
java -jar app.jar --spring.profiles.active=prod
# or
java -jar app.jar --game.production=true --game.auth.enabled=true \
  --game.auth.jwt.secret="…"
```

Profile file: `service/src/main/resources/application-prod.properties`.

## Secrets

```bash
cp deploy/.env.example deploy/.env
./scripts/gen-secrets.sh >> deploy/.env   # strong random values
# edit deploy/.env — set DOMAIN, live storefront keys if needed
```

Never commit `deploy/.env` or `deploy/certs/*`.

## TLS (HTTPS)

Nginx terminates TLS in front of the sticky app pair:

```bash
mkdir -p deploy/certs
# Let's Encrypt / your CA:
cp fullchain.pem privkey.pem deploy/certs/

docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  up --build -d
```

- HTTP → HTTPS redirect  
- TLS 1.2 / 1.3  
- HSTS + basic hardening headers  
- WebSocket upgrade preserved  

Cert paths inside the proxy: `/etc/nginx/certs/fullchain.pem` + `privkey.pem`.

## Multi-node reminder

Engines stay process-local — sticky sessions (nginx `ip_hash`) + shared JDBC/Redis
auth + shared saves volume. See [`MULTI_NODE.md`](MULTI_NODE.md).

## Quick verify

```bash
# Should fail with insecure defaults
GAME_PRODUCTION=true GAME_AUTH_ENABLED=true \
  java -jar service/target/ai-dungeon-master-service-*.jar

# Should pass with hardened env from gen-secrets.sh
```

## Health probes

| Path | Use | Status codes |
|---|---|---|
| `GET /health` | Liveness (process up) | always 200 when listening |
| `GET /health/ready` | Readiness — probes JDBC/Redis/file when configured | 200 UP / **503** DOWN |
| `GET /v2/health` | Versioned health (lean by default) | 200 / 503 |

**Public (unauthenticated) responses are lean:** `status`, `probe` (and on
`/v2/health`: `uptimeSeconds`, `detail=false`). Session/engine counts,
dependency maps, and memory stats are **not** exposed without an ops token:

- `X-Metrics-Token: <game.metrics.scrape-token>` or `Authorization: Bearer …`
  (also accepts `game.metrics.scrape-token.previous` during rotation)
- `X-Admin-Token: <game.admin.token>` (or `game.admin.token.previous` during rotation)

`AuthDependencyProbe` only checks backends your store config actually uses
(`memory` → `NOT_CONFIGURED`). Compose healthchecks hit `/health/ready` so a
node is not marked healthy until Postgres/Redis answer.

OpenAPI: [`docs/api/openapi.yaml`](api/openapi.yaml) (`HealthApi` + `getHealthV2`).

## Prometheus metrics

`GET /metrics` — Prometheus text exposition (token-gated when configured). Useful scrapes:

| Metric | Meaning |
|---|---|
| `dm_up` | Process answering scrapes |
| `dm_ready` | Auth backends healthy (same as readiness) |
| `dm_sessions_active` / `dm_engines_active` | Live load |
| `dm_dependency_up{name=…}` | Per-backend UP/DOWN (omitted if not configured) |
| `jvm_memory_bytes{area,id}` | Heap / non-heap |

Point Prometheus at each engine node; keep scrape on a private network or
gateway ACL (no JWT).

Sample stack overlay:

```bash
docker compose -f deploy/docker-compose.yml \
               -f deploy/docker-compose.metrics.yml \
               up -d
# Prometheus: http://localhost:9090
# Grafana:      http://localhost:3000  (admin/admin)
# Alertmanager: http://localhost:9093  (see deploy/prometheus/README.md)
```

### Slack / PagerDuty

```bash
./scripts/render-alertmanager.sh   # needs SLACK_* + PAGERDUTY_ROUTING_KEY
ALERTMANAGER_CONFIG=alertmanager.active.yml \
  docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.metrics.yml up -d alertmanager
```

See `deploy/alertmanager/alertmanager.receivers.yml` and `templates/dm.tmpl`.


## Metrics scrape auth

When `game.metrics.scrape-token` is set (required in production), scrapers must send:


```http
GET /metrics
X-Metrics-Token: <token>
# or Authorization: Bearer <token>
```

During rotation, set `GAME_METRICS_SCRAPE_TOKEN` to the new secret and
`GAME_METRICS_SCRAPE_TOKEN_PREVIOUS` (`game.metrics.scrape-token.previous`) to the
old one so scrapers can roll without downtime. Health detail accepts the same pair.

Launch smoke asserts this when `METRICS_TOKEN` is set (`launch-check` always sets one):
unauthenticated scrapes must get **401**, and both header forms must return **200**.

Prometheus config example:

```yaml
scrape_configs:
  - job_name: dm-engines
    authorization:
      type: Bearer
      credentials: ${GAME_METRICS_SCRAPE_TOKEN}
```

## STOMP subscription ACL

With auth on, clients may only `SUBSCRIBE` to `/topic/narrative/{theirSessionId}`.
Cross-session narrative eavesdropping is rejected by `StompAuthChannelInterceptor`.
Anonymous `CONNECT` is rejected when `game.auth.enabled=true`. Shared
`/topic/narrative` is disabled under auth. WebSocket frames are size-capped
(`game.ws.message-size-limit`, default 256 KiB).

Native endpoint: `ws(s)://host/ws-stomp`. SockJS fallback: `/ws`.

### STOMP smoke (launch gate)

With a running engine (auth on):

```bash
# full play path + STOMP ACL (mint/session already inside launch-smoke)
BASE_URL=http://127.0.0.1:8080 ADMIN_TOKEN=… ./scripts/launch-smoke.sh

# or STOMP-only against an existing session
BASE_URL=… TOKEN=… SESSION_ID=… node scripts/stomp-smoke.mjs
```

`scripts/stomp-smoke.mjs` (Node 22+ WebSocket) verifies JWT CONNECT, own-topic
subscribe, foreign-topic deny, shared-topic deny, and anonymous CONNECT reject.
Set `SKIP_STOMP=1` to skip when Node is unavailable.

## Marketplace SSRF guard

Remote pack/index downloads only allow `http(s)` URLs that resolve to **public** IPs.
Loopback, private, link-local, and CGNAT targets are rejected; redirects are disabled.

## Rate limits

`RateLimitFilter` applies fixed-window per-IP limits (override via env):

| Property | Default (dev) | Prod profile |
|---|---|---|
| `game.rate-limit.enabled` | true | true |
| `game.rate-limit.session-per-minute` | 30 | 20 |
| `game.rate-limit.logout-per-minute` | 20 | 15 |
| `game.rate-limit.admin-per-minute` | 30 | 20 |
| `game.rate-limit.install-per-minute` | 15 | 10 |
| `game.rate-limit.narrate-per-minute` | 20 | 12 |
| `game.rate-limit.action-per-minute` | 60 | 40 |
| `game.rate-limit.save-per-minute` | 30 | 20 |
| `game.rate-limit.metrics-per-minute` | 120 | 60 |
| `game.rate-limit.verify-per-minute` | 60 | 40 |
| `game.rate-limit.store` | `memory` | `redis` (shared across nodes) |

Limits are bound once via `RateLimitProperties`. Set `game.rate-limit.trust-forwarded-headers=true` **only** behind a trusted reverse proxy (otherwise clients can spoof `X-Forwarded-For` and bypass IP buckets).

Limits are bound once via `RateLimitProperties` (`game.rate-limit.*`) and shared by the HTTP filter + STOMP guards.

Covered paths: `POST /v2/session`, `POST /v2/session/refresh`, `DELETE /v2/session`, `/v2/admin/**`, `POST /v2/marketplace/{id}/install`, `POST /v2/catalog/packs`, `POST /v2/narrate` (+ STOMP `/app/narrate` per session), `POST /v2/action` (+ STOMP `/app/action` per session), `POST /v2/save|/load|/reset`, `GET /metrics`, `POST /v2/entitlements/verify`.
429 responses include `Retry-After` and `X-RateLimit-*` headers. Client IP prefers
`X-Forwarded-For` (nginx sets this).

Set `game.rate-limit.store=redis` (and a reachable `game.auth.redis.url`) so all
engine nodes share the same per-IP buckets. Memory store is per-process only.

## Remote marketplace index

Set `game.marketplace.remote-url` to a JSON index (see `docs/marketplace/index.example.json`).
Listings merge with the local `content-packs/` tree (local wins on id clash).
Remote install downloads the pack zip and runs it through `PackUploadService`.
Cache: `game.marketplace.remote-cache-seconds` (default 300).

### Marketplace integrity

| Property | Meaning |
|---|---|
| `game.marketplace.require-checksums` | Hide/reject remote packs without `sha256` (prod: true) |
| `game.marketplace.remote-hmac-secret` | HMAC-SHA256 of index body (`X-Marketplace-Signature` or JSON `signature`) |

Each remote pack may include `sha256` (hex). Downloads are verified before install.
Sign index files with `scripts/sign-marketplace-index.sh` (header form preferred).

### Async marketplace install

```http
POST /v2/marketplace/{id}/install?async=true   → 202 { jobId, phase, percent, … }
POST /v2/marketplace/{id}/install-async        → same (typed SDK path)
GET  /v2/marketplace/jobs/{jobId}              → progress (owner session only)
DELETE /v2/marketplace/jobs/{jobId}            → cancel (owner session only)
```

Phases: `QUEUED` → `DOWNLOADING` → `VERIFYING` → `INSTALLING` → `DONE` | `FAILED` | `CANCELLED`.

**Job ownership:** async installs **require** a Bearer session and bind to that
session id. Poll and cancel for other sessions (or no session) return **404**
(no existence leak); ownership denials still emit `security_audit outcome=forbidden`.
Ownerless legacy rows are fail-closed (not world-readable). Multi-node Redis
snapshots store `ownerSessionId`.

**Security audit:** foreign job poll/cancel emits
`security_audit outcome=forbidden` on logger `dm.security.audit` (caller + owner
session ids, no JWTs). Metrics scrape failures emit
`security_audit outcome=unauthorized path=/metrics`. Failed health-detail ops
tokens (`X-Metrics-Token` / `X-Admin-Token` present but wrong) emit
`unauthorized` on `/v2/health` and readiness paths while the response stays lean.
Rate-limit bursts emit `security_audit outcome=rate_limited` with bucket, count,
and retry-after (logger `dm.security.audit`). The same events are kept in a
process-local ring (last 200, newest first) and listed via
**`GET /v2/admin/security-events`** (System tab → Load events).

### Marketplace install job store

| Property | Meaning |
|---|---|
| `game.marketplace.jobs.store` | `memory` (default) or `redis` (prod) |
| `game.marketplace.jobs.ttl-seconds` | Redis key TTL (default 3600) |

Job snapshots (`phase`, bytes, cancel, owner) are written to Redis so other nodes can poll progress. Download workers remain process-local; orphaned non-terminal jobs report `FAILED` after restart.

## Live storefronts (Play / App Store / Steam)

| Property | Env equivalent |
|---|---|
| `game.storefront.google.package-name` | `STOREFRONT_GOOGLE_PACKAGE_NAME` |
| `game.storefront.google.access-token` | `STOREFRONT_GOOGLE_ACCESS_TOKEN` |
| `game.storefront.google.service-account-json` | `STOREFRONT_GOOGLE_SERVICE_ACCOUNT_JSON` (path to SA key; auto-mints Publisher API tokens) |
| `game.storefront.google.auto-acknowledge` | `STOREFRONT_GOOGLE_AUTO_ACKNOWLEDGE` (default true) |
| `game.storefront.google.auto-consume` | `STOREFRONT_GOOGLE_AUTO_CONSUME` (default false) |
| `game.storefront.apple.shared-secret` | `STOREFRONT_APPLE_SHARED_SECRET` |
| `game.storefront.apple.bundle-id` | `STOREFRONT_APPLE_BUNDLE_ID` |
| `game.storefront.steam.publisher-key` / `app-id` | `STOREFRONT_STEAM_*` |

`GET /v2/entitlements/storefronts` reports which plugins are **live** vs sandbox.

### Clients
- **Android**: Play Billing → JSON `{packageName,productId,purchaseToken}` → verify `google_play`
- **iOS**: StoreKit 2 → `{receiptData,productId}` → verify `app_store`
- Sandbox HMAC mint remains for local/CI

## Entitlement-gated content packs

`pack.yaml` may declare:

```yaml
requiredProductId: "pack_the_hollows"
# or:
# requiredProductIds: ["sku_a", "sku_b"]
# requireAllProducts: false   # any one SKU (default)
```

With `game.content.entitlement-gates=true` (default):

- Install leaves gated packs **disabled**
- `POST /v2/catalog/packs/{id}/enable` requires a session that owns the SKU(s) → else **402**
- Catalog `PackInfo.locked` / `requiredProductIds` surface the gate to clients

Note: `ContentRegistry` is process-wide; gating protects the **enable** action for the calling session (typical single-player / host console).

## Receipt replay protection

| Property | Default | Meaning |
|---|---|---|
| `game.auth.receipt-ledger.enabled` | `true` | Reject already-redeemed receipts |
| `game.auth.receipt-ledger.store` | `memory` / prod `redis` | Ledger backend (`memory`\|`redis`\|`jdbc`) |
| `game.auth.receipt-ledger.ttl-seconds` | `7776000` (90d) | Redis key TTL |

Fingerprint = SHA-256(`storefront + productId + receipt`). Same session re-submitting the same receipt is **idempotent**; other sessions get `receipt already redeemed`.

## Auto-enable packs on grant

With `game.content.auto-enable-on-grant=true` (default), a successful
`POST /v2/entitlements/verify` enables every installed pack whose
`requiredProductId(s)` are satisfied by the session. Response field
`enabledPacks` lists pack ids turned on for this call.

### JDBC receipt ledger

Set `game.auth.receipt-ledger.store=jdbc` with the same `game.auth.jdbc.*` pool used for sessions/entitlements. Table `dm_receipts` is auto-created (`fingerprint` PK). TTL is enforced lazily on read.

## Admin receipt inventory

| Property | Default | Meaning |
|---|---|---|
| `game.admin.token` | empty (disabled) | Shared secret for ops routes (`X-Admin-Token`) |
| `game.admin.token.previous` | empty | Optional previous token during rotation (`GAME_ADMIN_TOKEN_PREVIOUS`) |

```http
GET /v2/admin/receipts?limit=50&productId=sku_gold&storefront=dev&sessionId=…&since=…&until=…
X-Admin-Token: <game.admin.token>
```

Optional filters: `productId`, `storefront`, `sessionId`, `since` / `until` (epoch ms).

During rotation, set `GAME_ADMIN_TOKEN` to the new value and
`GAME_ADMIN_TOKEN_PREVIOUS` to the old value so both are accepted until scrapers
and runbooks are updated; then clear previous.

## Catalog pack upload (multi-tenant)

| Property | Dev default | Prod default | Meaning |
|---|---|---|---|
| `game.catalog.upload.enabled` | `true` | `true` | When false, `POST /v2/catalog/packs` → 403 |
| `game.catalog.upload.require-admin` | `false` | **`true`** | When true, upload needs valid `X-Admin-Token` (current or previous) |

Prod multi-tenant deployments should keep `require-admin=true` so anonymous sessions
cannot inject content packs. Local/dev keeps uploads open for mod tooling.


Returns fingerprint + sessionId + productId + storefront + redeemedAt (never raw receipts).
Disabled with 404 when token is blank. JWT is not required (`/v2/admin/**` is public to session auth).

## Session-scoped content packs

With `game.content.session-scoped-enable=true` (default):

- Catalog enable/disable is **per session**
- Auto-enable on grant writes session overrides only
- Process-wide `ContentRegistry` defaults stay for free packs; gated packs remain process-disabled
- `SessionContentFilter` installs a ThreadLocal enabled-pack set for the request so loot/encounters only use that session's packs

Set `false` to restore legacy process-wide toggles (single-player / single-tenant).

### Session pack store backends

| Property | Default | Prod |
|---|---|---|
| `game.content.session-packs.store` | `memory` | `redis` (or `jdbc`) |

- **redis** — hash `{prefix}:session-packs:{sessionId}`
- **jdbc** — table `dm_session_packs` (auto-created)

## Session hygiene (pack cleanup)

| Property | Default | Meaning |
|---|---|---|
| `game.auth.session.hygiene.enabled` | `true` | Periodic idle session purge |
| `game.auth.session.idle-ttl-seconds` | `86400` (24h) | Last-seen age before expiry |
| `game.auth.session.hygiene-interval-ms` | `300000` | Reaper interval |

On purge: session row deleted, **session pack overrides cleared**, idle game engine destroyed.

## Explicit logout

```http
DELETE /v2/session
Authorization: Bearer <token>
```

Drops the session, clears session pack overrides, and destroys the live game engine.
Clients should discard the token and mint a new session to continue.

Ops: `GET /v2/admin/session-packs?sessionId=…` lists enabled packs + overrides (`X-Admin-Token`).
`GET /v2/admin/sessions` lists active identities (no JWTs); `DELETE /v2/admin/sessions/{id}`
revokes identity and destroys the session engine (save-on-evict).

### Security audit surface (`dm.security.audit`)

| Outcome | When |
|---|---|
| `forbidden` | Marketplace install job poll/cancel by non-owner |
| `unauthorized` | Metrics scrape fail; health detail bad ops token |
| `rate_limited` | HTTP 429 buckets; STOMP narrate/action budget denials |
| `request_too_large` | HTTP 413 from `Content-Length` or stream body over max |


### Pack upload size

Already capped by Spring multipart: `spring.servlet.multipart.max-file-size=10MB`
(request 12MB). Service also caps uncompressed zip contents (~20 MB).

### Rate-limit metrics

`GET /metrics` exposes process counters:

| Metric | Labels | Meaning |
|---|---|---|
| `dm_rate_limit_rejected_total` | `bucket` | 429 / STOMP deny count |
| `dm_rate_limit_allowed_total` | `bucket` | checks that passed |

Buckets include: `session`, `logout`, `admin`, `install`, `narrate`, `narrate_stomp`, `action`, `action_stomp`, `save`, `metrics`, `verify`.
Counters reset on process restart.

## CORS and security headers

| Property | Dev default | Prod |
|---|---|---|
| `game.cors.allowed-origins` | `*` | **required explicit list** (no `*`) |
| `game.cors.allowed-methods` | REST verbs | same |
| `game.security.headers.enabled` | `true` | `true` |
| `game.security.hsts.enabled` | `false` | `true` |
| `game.security.frame-options` | `DENY` | `DENY` |

Headers added on every response: `X-Content-Type-Options`, `X-Frame-Options`,
`Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy`,
`Content-Security-Policy`, and `Strict-Transport-Security` when HSTS is on + HTTPS.

WebSocket (`/ws`, `/ws-stomp`) uses the same origin allow-list.

Set e.g. `game.cors.allowed-origins=https://play.example.com,https://admin.example.com`.

### HTTP body size limits

| Knob | Dev | Prod |
|---|---|---|
| `game.http.max-request-bytes` | 1 MiB | 512 KiB |
| Tomcat form/swallow/post (aligned) | via `TomcatBodyLimitCustomizer` + `server.tomcat.*` | same |
| Request body stream cap | `RequestSizeFilter` | same |

| `server.tomcat.max-http-form-post-size` | 1MB | 512KB |
| multipart pack upload | 10MB file / 12MB request | same |

Oversized requests with `Content-Length` are rejected early with **413** envelope.
