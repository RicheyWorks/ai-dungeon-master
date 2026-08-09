#!/usr/bin/env bash
# Goal G7 — storefront vertical smoke: mint → dev receipt → verify → pack unlock.
#   BASE_URL=http://127.0.0.1:8080 ./scripts/storefront-smoke.sh
set -eo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
BASE_URL="${BASE_URL%/}"
SKU="${STOREFRONT_SKU:-pack_the_hollows}"
SECRET="${STOREFRONT_DEV_SECRET:-dev-storefront-insecure-secret-change-me}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

red() { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
info() { printf '→ %s\n' "$*"; }

command -v curl >/dev/null || { red "curl required"; exit 2; }
command -v python3 >/dev/null || { red "python3 required"; exit 2; }
command -v openssl >/dev/null || { red "openssl required"; exit 2; }

http() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  local tmp; tmp="$(mktemp)"
  local args=(-sS -m 10 -X "$method" -H "Accept: application/json" -o "$tmp" -w "%{http_code}")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  if [[ -n "$data" ]]; then
    args+=(-H "Content-Type: application/json" -d "$data")
  fi
  CODE="$(curl "${args[@]}" "${BASE_URL}${path}" || echo 000)"
  BODY="$(cat "$tmp" 2>/dev/null || true)"
  rm -f "$tmp"
}

expect() {
  local want="$1" step="$2"
  if [[ "$CODE" != "$want" ]]; then
    red "FAIL $step: HTTP $CODE (want $want)"
    echo "$BODY" | head -c 400; echo
    exit 1
  fi
  green "OK $step ($CODE)"
}

# Mint session
info "Mint session"
http POST /v2/session "" '{"displayName":"StorefrontSmoke"}'
expect 200 "session mint"
TOKEN="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["payload"]["token"])' <<<"$BODY")"
SID="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["payload"]["sessionId"])' <<<"$BODY")"

# Build dev receipt: base64url(productId).base64url(hmac)
b64url() { openssl base64 -e -A | tr '+/' '-_' | tr -d '='; }
PROD_B64="$(printf '%s' "$SKU" | b64url)"
SIG_B64="$(printf '%s' "$SKU" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64url)"
RECEIPT="${PROD_B64}.${SIG_B64}"

info "Verify receipt for $SKU"
http POST /v2/entitlements/verify "$TOKEN" \
  "$(python3 -c 'import json,sys; print(json.dumps({"storefront":"dev","productId":sys.argv[1],"receipt":sys.argv[2]}))' "$SKU" "$RECEIPT")"
expect 200 "entitlements verify"
GRANTED="$(python3 -c 'import json,sys; p=json.load(sys.stdin)["payload"]; print(p.get("granted") or p.get("_granted") or False)' <<<"$BODY")"
if [[ "$GRANTED" != "True" && "$GRANTED" != "true" ]]; then
  red "FAIL grant: $BODY"
  exit 1
fi
green "OK granted $SKU"

info "List entitlements"
http GET /v2/entitlements "$TOKEN"
expect 200 "entitlements list"
python3 -c '
import json,sys
p=json.load(sys.stdin)["payload"]
prods=p.get("products") or p.get("owned") or []
print("products:", prods)
' <<<"$BODY"

info "Catalog — hollows should be unlockable/enabled if present"
http GET /v2/catalog "$TOKEN"
expect 200 "catalog"
python3 -c '
import json,sys
p=json.load(sys.stdin)["payload"]
packs=p.get("packs") or []
for pack in packs:
    pid=pack.get("id") or pack.get("packId")
    if pid and "hollow" in str(pid).lower():
        print(pid, "enabled=", pack.get("enabled"), "locked=", pack.get("locked"))
' <<<"$BODY"

green "Storefront smoke passed (session=$SID sku=$SKU)"
