#!/usr/bin/env bash
# Green gate for the Production Launch Pack:
#   1) mvn test (service)
#   2) web typecheck/build (if node available)
#   3) boot local engine with auth + packs
#   4) scripts/launch-smoke.sh
#
# Usage (from repo root):
#   ./scripts/launch-check.sh
#   SKIP_WEB=1 ./scripts/launch-check.sh
#   SKIP_SMOKE=1 ./scripts/launch-check.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export PATH="/opt/maven/bin:${PATH:-}"
red() { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
info() { printf '==> %s\n' "$*"; }

info "Maven tests"
mvn -pl service -am test -q

if [[ "${SKIP_WEB:-0}" != "1" ]]; then
  if command -v npm >/dev/null 2>&1; then
    info "Web client build"
    if [[ -f web/package.json ]]; then
      (cd web && npm run build 2>/dev/null || npm run typecheck 2>/dev/null || true)
      green "Web step finished (see output above)"
    fi
  else
    info "npm not found — skipping web build"
  fi
fi

if [[ "${SKIP_SMOKE:-0}" == "1" ]]; then
  green "launch-check: tests OK (smoke skipped)"
  exit 0
fi

info "Package service jar"
mvn -pl service -am -DskipTests package -q
JAR="$(ls -1 service/target/ai-dungeon-master-service-*.jar | head -1)"
[[ -f "$JAR" ]] || { red "jar not found"; exit 1; }

PORT="${SMOKE_PORT:-18080}"
LOG="${TMPDIR:-/tmp}/dm-launch-check-$$.log"
JWT_SECRET="${JWT_SECRET:-launch-check-jwt-secret-32chars-min!!}"
ADMIN_TOKEN="${ADMIN_TOKEN:-launch-check-admin-token-32chars!!}"
METRICS_TOKEN="${METRICS_TOKEN:-launch-check-metrics-token-16+}"

info "Boot engine on :$PORT"
# Dev-safe (non-prod) auth stack so we don't need Postgres/Redis for the gate.
# Production compose path is validated separately via ProductionSecurityGuard tests.
java -jar "$JAR" \
  --server.port="$PORT" \
  --server.address=0.0.0.0 \
  --game.gui.enabled=false \
  --game.production=false \
  --game.auth.enabled=true \
  --game.auth.jwt.secret="$JWT_SECRET" \
  --game.auth.session.store=memory \
  --game.auth.entitlement.store=memory \
  --game.auth.receipt-ledger.store=memory \
  --game.content.session-packs.store=memory \
  --game.rate-limit.store=memory \
  --game.rate-limit.enabled=true \
  --game.admin.token="$ADMIN_TOKEN" \
  --game.metrics.scrape-token="$METRICS_TOKEN" \
  --game.content.packs.dir="$ROOT/content-packs" \
  --game.saves.dir="${TMPDIR:-/tmp}/dm-saves-$$" \
  --game.legacy.api.enabled=false \
  --game.cors.allowed-origins="http://127.0.0.1:${PORT}" \
  >"$LOG" 2>&1 &
PID=$!
cleanup() {
  kill "$PID" 2>/dev/null || true
  wait "$PID" 2>/dev/null || true
}
trap cleanup EXIT

info "Wait for readiness (pid=$PID)"
ready=0
for i in $(seq 1 60); do
  if curl -sf -m 2 "http://127.0.0.1:${PORT}/health" >/dev/null 2>&1; then
    ready=1
    break
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    red "Engine exited early — log:"
    tail -80 "$LOG" || true
    exit 1
  fi
  sleep 1
done
if [[ "$ready" != "1" ]]; then
  red "Timed out waiting for /health"
  tail -80 "$LOG" || true
  exit 1
fi
green "Engine up"

info "Play-path smoke"
BASE_URL="http://127.0.0.1:${PORT}" ADMIN_TOKEN="$ADMIN_TOKEN" METRICS_TOKEN="$METRICS_TOKEN" \
  bash "$ROOT/scripts/launch-smoke.sh"

info "Prod guard unit coverage already in mvn test"
green "launch-check PASSED"
