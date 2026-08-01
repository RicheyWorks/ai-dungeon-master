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

| Path | Use |
|---|---|
| `GET /health` | Liveness (Docker / k8s) |
| `GET /health/ready` | Readiness + session/engine counts |
| `GET /v2/health` | Metrics envelope (uptime, memory) — public, no auth |

Compose healthchecks already hit `/health`.
