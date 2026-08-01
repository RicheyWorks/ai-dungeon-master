# Deploy — multi-node Docker stack

Runs **two engine nodes** behind nginx with **sticky IP hashing**, sharing
auth state (Postgres by default) and a saves volume.

```text
browser → nginx:8080 → app1 / app2  (sticky)
                         ↓
                   postgres + redis
                   shared /data/saves
```

## Quick start

```bash
# from repo root
docker compose -f deploy/docker-compose.yml up --build
```

Open:

| URL | What |
|---|---|
| http://localhost:8080/app/ | Full web client |
| http://localhost:8080/mod-browser.html | Pack admin |
| http://localhost:8080/health | Liveness |
| http://localhost:8080/v2/health | Metrics envelope |
| http://localhost:8080/metrics | Prometheus scrape |
| http://localhost:8080/v2/catalog | API |

Stop with `Ctrl+C` or `docker compose -f deploy/docker-compose.yml down`.

## Auth configuration (baked into compose)

| Setting | Value |
|---|---|
| `game.auth.enabled` | `true` |
| `game.auth.jwt.secret` | shared compose secret (**change in prod**) |
| sessions / entitlements | **jdbc** → Postgres |
| saves | shared Docker volume `saves` |
| sticky | nginx `ip_hash` |

### Redis instead of JDBC

```bash
docker compose \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.redis.yml \
  up --build
```

## Files

| Path | Role |
|---|---|
| [`../Dockerfile`](../Dockerfile) | Multi-stage Maven → JRE fat jar |
| `docker-compose.yml` | postgres, redis, app1, app2, proxy |
| `docker-compose.redis.yml` | overlay: `store=redis` |
| `nginx/default.conf` | sticky reverse proxy + WebSocket upgrade |

## Production notes

1. **Rotate** `GAME_AUTH_JWT_SECRET` and DB passwords  
2. Put TLS in front of nginx (or replace with a cloud LB that supports sticky + WebSockets)  
3. Sticky sessions keep engines + STOMP on one node; shared JDBC/Redis keeps identity portable  
4. Rebuild the image after UI changes: `./scripts/build-web.sh` then `docker compose build`  
5. Scale: add `app3` with the same env + volume, register it in `nginx/default.conf`

## Local jar without Docker

Still works:

```bash
./desktop/launch.sh
# or
java -jar service/target/ai-dungeon-master-service-*.jar
```

See also [`docs/MULTI_NODE.md`](../docs/MULTI_NODE.md).

## Production (TLS + secrets)

```bash
cp deploy/.env.example deploy/.env
./scripts/gen-secrets.sh >> deploy/.env
mkdir -p deploy/certs   # place fullchain.pem + privkey.pem

docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  up --build -d
```

Apps boot with `game.production=true` and refuse insecure defaults
(`ProductionSecurityGuard`). Full checklist: [`docs/PRODUCTION.md`](../docs/PRODUCTION.md).

## Metrics (Prometheus + Grafana)

```bash
docker compose -f deploy/docker-compose.yml \
               -f deploy/docker-compose.metrics.yml \
               up --build -d
# Prometheus → http://localhost:9090
# Grafana    → http://localhost:3000  (admin/admin)
```

Scrapes `app1` + `app2` at `/metrics`. Dashboard auto-loads under
*AI Dungeon Master*. Details: [`prometheus/README.md`](prometheus/README.md).

