# Prometheus + Alertmanager + Grafana for AI Dungeon Master

Scrapes each engine node’s public `GET /metrics` endpoint, evaluates alert
rules, and ships a provisioned Grafana dashboard.

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
| Alertmanager | http://localhost:9093 |
| Grafana | http://localhost:3000 (default `admin` / `admin`) |

- **Prometheus targets:** Status → Targets → `dm-engines` (app1 + app2 UP)
- **Alerts:** Status → Alerts (rules from `alert_rules.yml`)
- **Grafana dashboard:** folder *AI Dungeon Master* → *AI Dungeon Master — engines*

Override Grafana admin credentials:

```bash
GRAFANA_ADMIN_USER=ops GRAFANA_ADMIN_PASSWORD='…' \
  docker compose -f deploy/docker-compose.yml \
                 -f deploy/docker-compose.metrics.yml up -d
```

## Alert rules

| Alert | Expr (summary) | For | Severity |
|---|---|---|---|
| `DmEngineDown` | scrape `up == 0` | 1m | critical |
| `DmEngineNotReady` | `dm_ready == 0` | 2m | critical |
| `DmAuthDependencyDown` | `dm_dependency_up == 0` | 2m | warning |
| `DmHighEngineCount` | `dm_engines_active > 80` | 5m | warning |
| `DmHeapNearMax` | heap used/max > 90% | 5m | warning |

`DmEngineDown` inhibits readiness/dependency alerts on the same instance.

Default Alertmanager receivers are empty (UI only). Wire Slack/email in
[`../alertmanager/alertmanager.yml`](../alertmanager/alertmanager.yml).

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
| `ALERTS{alertstate="firing"}` | Currently firing alerts |

## Layout

```text
deploy/
  prometheus/prometheus.yml
  prometheus/alert_rules.yml
  alertmanager/alertmanager.yml
  grafana/…                          # dashboard + provisioning
  docker-compose.metrics.yml         # prometheus + alertmanager + grafana
```

## Production

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.prod.yml \
  -f deploy/docker-compose.metrics.yml \
  up -d
```

Do **not** expose ports `9090` / `9093` / `3000` publicly without auth.

See also: [`docs/PRODUCTION.md`](../../docs/PRODUCTION.md).

## Slack + PagerDuty receivers

1. Create a Slack incoming webhook and a PagerDuty Events API v2 integration.
2. Render the config (never commit the result):

```bash
export SLACK_WEBHOOK_URL='https://hooks.slack.com/services/…'
export SLACK_CHANNEL='#dm-alerts'
export PAGERDUTY_ROUTING_KEY='…'
./scripts/render-alertmanager.sh
```

3. Point Alertmanager at the rendered file:

```bash
ALERTMANAGER_CONFIG=alertmanager.active.yml \
  docker compose -f deploy/docker-compose.yml \
                 -f deploy/docker-compose.metrics.yml \
                 up -d alertmanager
```

Templates live in `deploy/alertmanager/templates/dm.tmpl`.
Critical alerts go to Slack **and** PagerDuty; warnings to Slack only.
