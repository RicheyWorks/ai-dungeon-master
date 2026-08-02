# Production deploy checklist

## Fail-fast security guard

When `game.production=true` **or** Spring profile `prod` is active, the service
runs `ProductionSecurityGuard` at boot and **refuses to start** if:

| Check | Requirement |
|---|---|
| Auth | `game.auth.enabled=true` |
| JWT secret | set, ≥ 32 chars, not a known dev default |
| Session / entitlement store | not `memory` (use `jdbc`, `redis`, or `file`) |
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
| `GET /v2/health` | Metrics + dependency map (public) | 200 / 503 |

`AuthDependencyProbe` only checks backends your store config actually uses
(`memory` → `NOT_CONFIGURED`). Compose healthchecks hit `/health/ready` so a
node is not marked healthy until Postgres/Redis answer.

OpenAPI: [`docs/api/openapi.yaml`](api/openapi.yaml) (`HealthApi` + `getHealthV2`).

## Prometheus metrics

`GET /metrics` — Prometheus text exposition (public). Useful scrapes:

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

## Rate limits

`RateLimitFilter` applies fixed-window per-IP limits (override via env):

| Property | Default (dev) | Prod profile |
|---|---|---|
| `game.rate-limit.enabled` | true | true |
| `game.rate-limit.session-per-minute` | 30 | 20 |
| `game.rate-limit.metrics-per-minute` | 120 | 60 |
| `game.rate-limit.verify-per-minute` | 60 | 40 |
| `game.rate-limit.store` | `memory` | `redis` (shared across nodes) |

Covered paths: `POST /v2/session`, `GET /metrics`, `POST /v2/entitlements/verify`.
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
GET  /v2/marketplace/jobs/{jobId}              → progress (DOWNLOADING/VERIFYING/INSTALLING/DONE)
DELETE /v2/marketplace/jobs/{jobId}            → cancel
```

Phases: `QUEUED` → `DOWNLOADING` → `VERIFYING` → `INSTALLING` → `DONE` | `FAILED` | `CANCELLED`.

### Marketplace install job store

| Property | Meaning |
|---|---|
| `game.marketplace.jobs.store` | `memory` (default) or `redis` (prod) |
| `game.marketplace.jobs.ttl-seconds` | Redis key TTL (default 3600) |

Job snapshots (`phase`, bytes, cancel) are written to Redis so other nodes can poll progress. Download workers remain process-local; orphaned non-terminal jobs report `FAILED` after restart.

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
| `game.admin.token` | empty (disabled) | Shared secret for ops routes |

```http
GET /v2/admin/receipts?limit=50&productId=sku_gold&storefront=dev&sessionId=…&since=…&until=…
X-Admin-Token: <game.admin.token>
```

Optional filters: `productId`, `storefront`, `sessionId`, `since` / `until` (epoch ms).


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

