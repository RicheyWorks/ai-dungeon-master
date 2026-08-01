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

Covered paths: `POST /v2/session`, `GET /metrics`, `POST /v2/entitlements/verify`.
429 responses include `Retry-After` and `X-RateLimit-*` headers. Client IP prefers
`X-Forwarded-For` (nginx sets this).
