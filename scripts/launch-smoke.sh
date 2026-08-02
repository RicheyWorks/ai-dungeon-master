#!/usr/bin/env bash
# End-to-end play path smoke against a running engine:
#   mint → me → catalog → enable pack → status → action → narrate →
#   save → load → entitlements → admin receipts → logout
#
#   BASE_URL=http://127.0.0.1:8080 ADMIN_TOKEN=... ./scripts/launch-smoke.sh
set -eo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
BASE_URL="${BASE_URL%/}"
REQUEST_ID="smoke-$(date +%s)-$$"
TIMEOUT="${SMOKE_TIMEOUT:-8}"
HTTP_CODE=""
BODY=""

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
http POST /v2/load "$TOKEN"
expect 200 "POST /v2/load"

http GET /v2/entitlements "$TOKEN"
expect 200 "GET /v2/entitlements"

if [[ -n "${ADMIN_TOKEN:-}" ]]; then
  tmp="$(mktemp)"
  HTTP_CODE="$(curl -sS -m "$TIMEOUT" -H "X-Admin-Token: $ADMIN_TOKEN" -H "X-Request-Id: $REQUEST_ID" \
    -o "$tmp" -w "%{http_code}" "${BASE_URL}/v2/admin/receipts?limit=5" || echo 000)"
  BODY="$(cat "$tmp")"; rm -f "$tmp"
  expect 200 "GET /v2/admin/receipts"
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
if [[ "$HTTP_CODE" == "200" ]] && printf '%s' "$BODY" | grep -qE 'dm_up|dm_sessions'; then
  green "OK  GET /metrics"
else
  info "metrics soft-fail HTTP $HTTP_CODE"
fi

green "Launch smoke PASSED against $BASE_URL"
