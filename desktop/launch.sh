#!/usr/bin/env bash
# Start the AI Dungeon Master engine (if needed) and open the web client.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${DM_PORT:-8080}"
HOST="${DM_HOST:-127.0.0.1}"
BASE="http://${HOST}:${PORT}"
APP_URL="${DM_APP_URL:-${BASE}/app/}"
JAR="${DM_JAR:-$ROOT/service/target/ai-dungeon-master-service-1.0-SNAPSHOT.jar}"
LOG="${DM_LOG:-/tmp/ai-dungeon-master-desktop.log}"

healthy() {
  curl -sf -o /dev/null --max-time 2 "${BASE}/v2/catalog" \
    || curl -sf -o /dev/null --max-time 2 "${BASE}/app/" \
    || curl -sf -o /dev/null --max-time 2 "${BASE}/app/index.html"
}

open_browser() {
  local url="$1"
  if command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$url" >/dev/null 2>&1 || true
  elif command -v open >/dev/null 2>&1; then
    open "$url" || true
  elif command -v wslview >/dev/null 2>&1; then
    wslview "$url" || true
  else
    echo "Open this URL in your browser: $url"
  fi
}

ensure_jar() {
  if [[ -f "$JAR" ]]; then
    return 0
  fi
  echo "[desktop] fat jar not found at $JAR"
  echo "[desktop] building service module…"
  (cd "$ROOT" && mvn -pl service -am -DskipTests package -q)
  if [[ ! -f "$JAR" ]]; then
    echo "[desktop] build finished but jar still missing: $JAR" >&2
    exit 1
  fi
}

STARTED_HERE=0
PID=""

cleanup() {
  if [[ "$STARTED_HERE" -eq 1 && -n "${PID}" ]]; then
    echo
    echo "[desktop] stopping engine (pid $PID)…"
    kill "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

if healthy; then
  echo "[desktop] engine already running at $BASE"
else
  ensure_jar
  echo "[desktop] starting $JAR"
  echo "[desktop] log → $LOG"
  java -jar "$JAR" >>"$LOG" 2>&1 &
  PID=$!
  STARTED_HERE=1

  echo -n "[desktop] waiting for engine"
  for _ in $(seq 1 60); do
    if healthy; then
      echo " ready."
      break
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
      echo
      echo "[desktop] engine exited early — see $LOG" >&2
      tail -n 40 "$LOG" >&2 || true
      exit 1
    fi
    echo -n "."
    sleep 0.5
  done
  if ! healthy; then
    echo
    echo "[desktop] timed out waiting for $BASE" >&2
    exit 1
  fi
fi

echo "[desktop] opening $APP_URL"
open_browser "$APP_URL"

if [[ "$STARTED_HERE" -eq 1 ]]; then
  echo "[desktop] engine running (pid $PID). Ctrl+C to stop."
  wait "$PID"
else
  echo "[desktop] left existing engine running."
fi
