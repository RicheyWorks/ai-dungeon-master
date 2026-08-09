#!/usr/bin/env bash
# End-to-end play path smoke against a running engine:
#   mint → me → catalog → enable pack → status → action → narrate →
#   save → load → entitlements → admin receipts → STOMP ACL → logout
#
#   BASE_URL=http://127.0.0.1:8080 ADMIN_TOKEN=... ./scripts/launch-smoke.sh
#   SKIP_STOMP=1 to skip WebSocket ACL checks (when Node WebSocket unavailable)
set -eo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
BASE_URL="${BASE_URL%/}"
REQUEST_ID="smoke-$(date +%s)-$$"
TIMEOUT="${SMOKE_TIMEOUT:-8}"
HTTP_CODE=""
BODY=""
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

red() { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
info() { printf '→ %s\n' "$*"; }

command -v curl >/dev/null || { red "curl required"; exit 2; }
command -v python3 >/dev/null || { red "python3 required"; exit 2; }

http() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  local tmp
  tmp="$(mktemp)"
  local args=(-sS -m "$TIMEOUT" -X "$method" -H "Accept: application/json" -H "X-Request-Id: $REQUEST_ID" -o "$tmp" -w "%{http_code}")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  if [[ -n "$data" ]]; then
    args+=(-H "Content-Type: application/json" -d "$data")
  fi
  HTTP_CODE="$(curl "${args[@]}" "${BASE_URL}${path}" || echo 000)"
  BODY="$(cat "$tmp" 2>/dev/null || true)"
  rm -f "$tmp"
}

expect() {
  local want="$1" step="$2"
  if [[ "$HTTP_CODE" != "$want" ]]; then
    red "FAIL $step: expected HTTP $want got $HTTP_CODE"
    printf '%s\n' "$BODY" | head -c 600; echo
    exit 1
  fi
  green "OK  $step (HTTP $HTTP_CODE)"
}

json_get() {
  python3 -c "
import json,sys
o=json.loads(sys.argv[1])
cur=o
for part in sys.argv[2].split('.'):
    if isinstance(cur, dict):
        cur=cur.get(part)
    else:
        cur=None
        break
print('' if cur is None else cur)
" "$BODY" "$1"
}

pick_pack() {
  python3 -c "
import json,sys
o=json.loads(sys.argv[1])
packs=(o.get('payload') or {}).get('contentPacks') or []
cands=[]
for p in packs:
    if not isinstance(p, dict):
        continue
    if p.get('locked'):
        continue
    i=p.get('id')
    if not i or i=='builtin':
        continue
    cands.append((0 if not p.get('enabled', True) else 1, i))
cands.sort()
print(cands[0][1] if cands else '')
" "$BODY"
}

pick_choice() {
  python3 -c "
import json,sys
o=json.loads(sys.argv[1])
c=(o.get('payload') or {}).get('availableChoices') or []
print(c[0] if c else '')
" "$BODY"
}

info "Smoke against $BASE_URL (requestId=$REQUEST_ID)"

for _ in $(seq 1 15); do
  http GET /health
  [[ "$HTTP_CODE" == "200" ]] && break
  sleep 1
done
expect 200 "GET /health"

# Public health must stay lean (no recon detail without ops token).
if printf '%s' "$BODY" | python3 -c "import json,sys; o=json.load(sys.stdin); sys.exit(0 if 'sessions' not in o and 'dependencies' not in o else 1)"; then
  green "OK  GET /health lean (no sessions/dependencies)"
else
  red "FAIL /health leaked recon fields without token"
  exit 1
fi

http POST /v2/session "" '{"displayName":"SmokeTester"}'
expect 200 "POST /v2/session"
TOKEN="$(json_get payload.token)"
SESSION_ID="$(json_get payload.sessionId)"
if [[ -z "$TOKEN" || -z "$SESSION_ID" ]]; then
  red "missing token/sessionId in body: $BODY"
  exit 1
fi
info "sessionId=$SESSION_ID"

http GET /v2/session/me "$TOKEN"
expect 200 "GET /v2/session/me"

http POST /v2/session/refresh "$TOKEN"
expect 200 "POST /v2/session/refresh"
NEW_TOKEN="$(json_get payload.token)"
NEW_SID="$(json_get payload.sessionId)"
if [[ -n "$NEW_TOKEN" ]]; then
  if [[ -n "$SESSION_ID" && -n "$NEW_SID" && "$NEW_SID" != "$SESSION_ID" ]]; then
    red "FAIL refresh changed sessionId"
    exit 1
  fi
  TOKEN="$NEW_TOKEN"
  green "OK  session refresh kept id, rotated token"
fi

http PATCH /v2/session "$TOKEN" '{"displayName":"SmokeRenamed"}'
expect 200 "PATCH /v2/session (rename)"
RENAME_NAME="$(json_get payload.displayName)"
RENAME_TOKEN="$(json_get payload.token)"
if [[ -n "$RENAME_TOKEN" ]]; then
  TOKEN="$RENAME_TOKEN"
fi
if [[ "$RENAME_NAME" != "SmokeRenamed" ]]; then
  red "FAIL rename displayName got ${RENAME_NAME}"
  exit 1
fi
green "OK  session rename"

http GET /v2/catalog "$TOKEN"
expect 200 "GET /v2/catalog"
PACK_ID="$(pick_pack)"
if [[ -n "$PACK_ID" ]]; then
  info "enabling pack $PACK_ID"
  http POST "/v2/catalog/packs/${PACK_ID}/enable" "$TOKEN"
  expect 200 "POST enable pack $PACK_ID"
else
  info "no free content pack to enable (continue)"
fi

http GET /v2/status "$TOKEN"
expect 200 "GET /v2/status"
CHOICE="$(pick_choice)"
if [[ -n "$CHOICE" ]]; then
  info "action: $CHOICE"
  BODY_JSON="$(python3 -c "import json,sys; print(json.dumps({'choiceLabel': sys.argv[1]}))" "$CHOICE")"
  http POST /v2/action "$TOKEN" "$BODY_JSON"
  expect 200 "POST /v2/action"
else
  info "no availableChoices (skip action)"
fi

http POST /v2/narrate "$TOKEN" '{"prompt":"Describe the scene in one sentence."}'
expect 200 "POST /v2/narrate"

http POST /v2/save "$TOKEN"
expect 200 "POST /v2/save"
http GET /v2/save "$TOKEN"
expect 200 "GET /v2/save (meta)"
if [[ "$(json_get payload.exists)" != "true" ]]; then
  red "FAIL save meta exists not true"
  exit 1
fi
green "OK  save meta"
http POST /v2/load "$TOKEN"
expect 200 "POST /v2/load"
http DELETE /v2/save "$TOKEN"
expect 200 "DELETE /v2/save"
if [[ "$(json_get payload.exists)" == "true" ]]; then
  red "FAIL save still exists after delete"
  exit 1
fi
green "OK  save deleted"
# re-save so later steps that assume a save still work if any
http POST /v2/save "$TOKEN"
expect 200 "POST /v2/save (restore slot)"

http GET /v2/entitlements "$TOKEN"
expect 200 "GET /v2/entitlements"

# Typed async install path (202 job) when marketplace has a local pack.
http GET /v2/marketplace "$TOKEN"
if [[ "$HTTP_CODE" == "200" ]]; then
  PACK_ID="$(python3 -c "
import json,sys
o=json.loads(sys.argv[1])
packs=(o.get('payload') or {}).get('packs') or []
for p in packs:
    if isinstance(p, dict) and p.get('id') and not p.get('installed'):
        print(p['id']); break
else:
    for p in packs:
        if isinstance(p, dict) and p.get('id'):
            print(p['id']); break
" "$BODY")"
  if [[ -n "$PACK_ID" ]]; then
    info "async install path pack=$PACK_ID"
    http POST "/v2/marketplace/${PACK_ID}/install-async" "$TOKEN"
    if [[ "$HTTP_CODE" == "202" ]]; then
      green "OK  POST install-async (HTTP 202)"
      JOB_ID="$(json_get payload.jobId)"
      if [[ -n "$JOB_ID" ]]; then
        http GET "/v2/marketplace/jobs/${JOB_ID}" "$TOKEN"
        expect 200 "GET install job (owner)"
        http GET "/v2/marketplace/jobs?limit=10" "$TOKEN"
        expect 200 "GET install jobs list"
        COUNT="$(json_get payload.count)"
        if [[ -n "$COUNT" && "$COUNT" -lt 1 ]]; then
          red "FAIL jobs list empty after async install"
          exit 1
        fi
        green "OK  marketplace jobs list (count=${COUNT:-?})"
      fi
    else
      info "install-async returned HTTP $HTTP_CODE (pack may already be installing / gated) — non-fatal"
    fi
  fi
fi


if [[ -n "${ADMIN_TOKEN:-}" ]]; then
  tmp="$(mktemp)"
  HTTP_CODE="$(curl -sS -m "$TIMEOUT" -H "X-Admin-Token: $ADMIN_TOKEN" -H "X-Request-Id: $REQUEST_ID" \
    -o "$tmp" -w "%{http_code}" "${BASE_URL}/v2/admin/receipts?limit=5" || echo 000)"
  BODY="$(cat "$tmp")"; rm -f "$tmp"
  expect 200 "GET /v2/admin/receipts"

  tmp="$(mktemp)"
  HTTP_CODE="$(curl -sS -m "$TIMEOUT" -H "X-Admin-Token: $ADMIN_TOKEN" -H "X-Request-Id: $REQUEST_ID" \
    -o "$tmp" -w "%{http_code}" "${BASE_URL}/v2/admin/security-events?limit=10" || echo 000)"
  BODY="$(cat "$tmp")"; rm -f "$tmp"
  expect 200 "GET /v2/admin/security-events"
  green "OK  admin security-events"

  tmp="$(mktemp)"
  HTTP_CODE="$(curl -sS -m "$TIMEOUT" -H "X-Admin-Token: $ADMIN_TOKEN" -H "X-Request-Id: $REQUEST_ID" \
    -o "$tmp" -w "%{http_code}" "${BASE_URL}/v2/admin/audit-events?limit=10" || echo 000)"
  BODY="$(cat "$tmp")"; rm -f "$tmp"
  expect 200 "GET /v2/admin/audit-events"
  green "OK  admin audit-events"

  tmp="$(mktemp)"
  HTTP_CODE="$(curl -sS -m "$TIMEOUT" -H "X-Admin-Token: $ADMIN_TOKEN" -H "X-Request-Id: $REQUEST_ID" \
    -o "$tmp" -w "%{http_code}" "${BASE_URL}/v2/admin/narration" || echo 000)"
  BODY="$(cat "$tmp")"; rm -f "$tmp"
  expect 200 "GET /v2/admin/narration"
  green "OK  admin narration"

  tmp="$(mktemp)"
  HTTP_CODE="$(curl -sS -m "$TIMEOUT" -H "X-Admin-Token: $ADMIN_TOKEN" -H "X-Request-Id: $REQUEST_ID" \
    -o "$tmp" -w "%{http_code}" "${BASE_URL}/v2/admin/sessions?limit=20" || echo 000)"
  BODY="$(cat "$tmp")"; rm -f "$tmp"
  expect 200 "GET /v2/admin/sessions"
  SESS_TOTAL="$(json_get payload.total)"
  info "admin sessions total=${SESS_TOTAL:-?}"

  tmp="$(mktemp)"
  HTTP_CODE="$(curl -sS -m "$TIMEOUT" -X POST -H "X-Admin-Token: $ADMIN_TOKEN" -H "X-Request-Id: $REQUEST_ID" \
    -o "$tmp" -w "%{http_code}" \
    "${BASE_URL}/v2/admin/sessions/purge-idle?idleTtlSeconds=999999999&evictEngines=false" || echo 000)"
  BODY="$(cat "$tmp")"; rm -f "$tmp"
  expect 200 "POST /v2/admin/sessions/purge-idle"
fi

# STOMP JWT CONNECT + subscription ACL (requires Node 22+ WebSocket).
if [[ "${SKIP_STOMP:-0}" != "1" ]] && command -v node >/dev/null 2>&1; then
  info "STOMP ACL smoke"
  # Mint a second session so we have a real foreign session id to deny.
  http POST /v2/session "" '{"displayName":"SmokeOther"}'
  OTHER_SESSION_ID="$(json_get payload.sessionId)"
  OTHER_TOKEN="$(json_get payload.token)"
  if [[ -z "$OTHER_SESSION_ID" ]]; then
    OTHER_SESSION_ID="00000000-foreign-session"
  fi
  BASE_URL="$BASE_URL" TOKEN="$TOKEN" SESSION_ID="$SESSION_ID" \
    OTHER_SESSION_ID="$OTHER_SESSION_ID" \
    STOMP_TIMEOUT_MS="${STOMP_TIMEOUT_MS:-8000}" \
    node "$ROOT/scripts/stomp-smoke.mjs"
  # Best-effort logout of the foreign session (ignore failures).
  if [[ -n "${OTHER_TOKEN:-}" ]]; then
    http DELETE /v2/session "$OTHER_TOKEN" || true
  fi
else
  info "STOMP smoke skipped (SKIP_STOMP=1 or node missing)"
fi

http DELETE /v2/session "$TOKEN"
if [[ "$HTTP_CODE" != "200" && "$HTTP_CODE" != "204" ]]; then
  red "FAIL DELETE /v2/session got $HTTP_CODE"
  exit 1
fi
green "OK  DELETE /v2/session (HTTP $HTTP_CODE)"

http GET /v2/session/me "$TOKEN"
if [[ "$HTTP_CODE" == "401" || "$HTTP_CODE" == "403" ]]; then
  green "OK  post-logout rejected (HTTP $HTTP_CODE)"
else
  info "post-logout HTTP $HTTP_CODE"
fi

tmp="$(mktemp)"
HTTP_CODE="$(curl -sS -m "$TIMEOUT" -o "$tmp" -w "%{http_code}" "${BASE_URL}/metrics" || echo 000)"
BODY="$(cat "$tmp")"; rm -f "$tmp"
if [[ -n "${METRICS_TOKEN:-}" ]]; then
  if [[ "$HTTP_CODE" == "401" || "$HTTP_CODE" == "403" ]]; then
    green "OK  GET /metrics rejects unauthenticated (HTTP $HTTP_CODE)"
  else
    red "FAIL GET /metrics without token: expected 401/403 got $HTTP_CODE"
    exit 1
  fi
  tmp="$(mktemp)"
  HTTP_CODE="$(curl -sS -m "$TIMEOUT" -H "X-Metrics-Token: $METRICS_TOKEN" -o "$tmp" -w "%{http_code}" \
    "${BASE_URL}/metrics" || echo 000)"
  BODY="$(cat "$tmp")"; rm -f "$tmp"
  if [[ "$HTTP_CODE" == "200" ]] && printf '%s' "$BODY" | grep -qE 'dm_up|dm_sessions'; then
    green "OK  GET /metrics with X-Metrics-Token"
  else
    red "FAIL GET /metrics with token: HTTP $HTTP_CODE"
    printf '%s\n' "$BODY" | head -c 300; echo
    exit 1
  fi
  # Bearer form
  tmp="$(mktemp)"
  HTTP_CODE="$(curl -sS -m "$TIMEOUT" -H "Authorization: Bearer $METRICS_TOKEN" -o "$tmp" -w "%{http_code}" \
    "${BASE_URL}/metrics" || echo 000)"
  BODY="$(cat "$tmp")"; rm -f "$tmp"
  if [[ "$HTTP_CODE" == "200" ]] && printf '%s' "$BODY" | grep -q dm_up; then
    green "OK  GET /metrics with Bearer scrape token"
  else
    red "FAIL GET /metrics Bearer: HTTP $HTTP_CODE"
    exit 1
  fi
elif [[ "$HTTP_CODE" == "200" ]] && printf '%s' "$BODY" | grep -qE 'dm_up|dm_sessions'; then
  green "OK  GET /metrics (open — no METRICS_TOKEN)"
else
  info "metrics soft-fail HTTP $HTTP_CODE"
fi

green "Launch smoke PASSED against $BASE_URL"
