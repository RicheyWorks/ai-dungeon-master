# Multi-node deployments

The engine can run behind a load balancer with **shared session identity and
entitlements**. Per-session game engines stay **process-local**.

## Shared state

| Concern | memory | file (shared volume) | redis | jdbc (Postgres) |
|---|---|---|---|---|
| Player sessions | ✗ node-local | ✓ | ✓ | ✓ |
| Entitlements | ✗ node-local | ✓ | ✓ | ✓ |
| Game engines | always node-local | always node-local | always node-local | always node-local |
| Game saves | local dir | shared `game.saves.dir` | shared `game.saves.dir` | shared `game.saves.dir` |

## Redis mode

```properties
game.auth.jwt.secret=<same-on-every-node>
game.auth.session.store=redis
game.auth.entitlement.store=redis
game.auth.redis.url=redis://redis:6379
game.auth.redis.key-prefix=dm
game.saves.dir=/mnt/shared/saves
```

Keys written:

- `dm:session:{id}` — hash (`displayName`, `createdAt`, `lastSeen`)
- `dm:sessions` — set of session ids
- `dm:entitlements:{sessionId}` — set of product ids

## JDBC / Postgres mode

```properties
game.auth.jwt.secret=<same-on-every-node>
game.auth.session.store=jdbc
game.auth.entitlement.store=jdbc
game.auth.jdbc.url=jdbc:postgresql://db:5432/dungeon
game.auth.jdbc.username=dm
game.auth.jdbc.password=secret
# optional: game.auth.jdbc.driver=org.postgresql.Driver
game.saves.dir=/mnt/shared/saves
```

`postgres` is accepted as an alias for `jdbc`. On first use the service creates:

```sql
dm_sessions (id PK, display_name, created_at, last_seen_at)
dm_entitlements (session_id, product_id, granted_at, PRIMARY KEY (session_id, product_id))
```

Upserts use `INSERT … ON CONFLICT` on PostgreSQL and `MERGE` on H2 (tests).

## Sticky sessions for the live world

`GameInstanceService` holds engines in process memory. For multi-node:

1. **Prefer sticky sessions** (cookie / JWT hash) so a player stays on one node, or  
2. Rely on **autoload** (`game.instances.autoload=true`) + shared `game.saves.dir` so a cold node restores the last save (brief state lag possible).

STOMP is also per-node — sticky routing keeps the live narration socket on the same instance that holds the engine.

## File mode (no Redis / DB)

```properties
game.auth.session.store=file
game.auth.session.file=/mnt/shared/sessions.json
game.auth.entitlement.store=file
game.auth.entitlement.file=/mnt/shared/entitlements.json
```

Uses `LockedJsonFile` cross-process locks. Fine for small clusters on NFS/EFS;
Redis or JDBC is preferred under write contention.
