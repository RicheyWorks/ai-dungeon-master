# Prometheus + Grafana for AI Dungeon Master

Scrapes each engine node’s public `GET /metrics` endpoint and ships a
provisioned Grafana dashboard.

## Quick start

From the **repo root**:

```bash
docker compose -f deploy/docker-compose.yml \
               -f deploy/docker-compose.metrics.yml \
               up --build -d
```

| Service | URL |
|---|---|
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (default `admin` / `admin`) |

- **Prometheus targets:** Status → Targets → `dm-engines` (app1 + app2 UP)
- **Grafana dashboard:** folder *AI Dungeon Master* → *AI Dungeon Master — engines*
  (auto-provisioned; datasource points at `http://prometheus:9090`)

Override Grafana admin credentials:

```bash
GRAFANA_ADMIN_USER=ops GRAFANA_ADMIN_PASSWORD='…' \
  docker compose -f deploy/docker-compose.yml \
                 -f deploy/docker-compose.metrics.yml up -d
```

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

## Layout

```text
deploy/
  prometheus/prometheus.yml          # scrape app1 + app2
  grafana/
    provisioning/datasources/…       # Prometheus DS
    provisioning/dashboards/…        # file provider
    dashboards/ai-dungeon-master.json
  docker-compose.metrics.yml         # prometheus + grafana services
```

## Production

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  -f deploy/docker-compose.metrics.yml \
  up -d
```

Do **not** expose ports `9090` / `3000` publicly without auth. Prefer a VPN,
reverse-proxy auth, or remote-write to a managed backend.

See also: [`docs/PRODUCTION.md`](../../docs/PRODUCTION.md) (metrics section).
