# Prometheus for AI Dungeon Master

Scrapes each engine node’s public `GET /metrics` endpoint (Prometheus text).

## Quick start

From the **repo root**:

```bash
docker compose -f deploy/docker-compose.yml \
               -f deploy/docker-compose.metrics.yml \
               up --build -d
```

- **Prometheus UI:** http://localhost:9090  
- **Targets:** Status → Targets → `dm-engines` (app1 + app2 should be UP)  
- **Example query:** `dm_sessions_active` or `dm_ready`

## Why scrape apps, not nginx?

Engines are process-local. Sticky routing would pin scrapes to one node and hide
the other. The config targets `app1:8080` and `app2:8080` on the compose network.

## Useful queries

| Query | Meaning |
|---|---|
| `dm_up` | Node answering scrapes |
| `dm_ready` | Auth backends healthy on that node |
| `sum(dm_sessions_active)` | Total sessions across nodes |
| `dm_engines_active` | Per-node live engines |
| `dm_dependency_up{name="jdbc"}` | JDBC pool probe |
| `jvm_memory_bytes{area="heap",id="used"}` | Heap used |

## Production

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  -f deploy/docker-compose.metrics.yml \
  up -d
```

Do **not** expose port `9090` publicly without auth. Prefer a VPN, reverse-proxy
basic auth, or Prometheus remote-write to a managed backend and drop the local UI.

See also: [`docs/PRODUCTION.md`](../docs/PRODUCTION.md) (metrics section).
