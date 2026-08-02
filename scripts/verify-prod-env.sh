#!/usr/bin/env bash
# Validate deploy/.env has the keys ProductionSecurityGuard + compose prod require.
#   ./scripts/verify-prod-env.sh deploy/.env
set -euo pipefail

ENV_FILE="${1:-deploy/.env}"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "missing $ENV_FILE — copy deploy/.env.example and run scripts/gen-secrets.sh" >&2
  exit 2
fi

# shellcheck disable=SC1090
set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a

fail=0
need() {
  local k="$1"
  local v="${!k:-}"
  if [[ -z "$v" ]]; then
    echo "FAIL: $k is empty"
    fail=1
  fi
}

need POSTGRES_PASSWORD
need GAME_AUTH_JWT_SECRET
need GAME_AUTH_JDBC_PASSWORD
need GAME_ADMIN_TOKEN
need GAME_CORS_ALLOWED_ORIGINS
need GAME_METRICS_SCRAPE_TOKEN

if [[ -n "${GAME_AUTH_JWT_SECRET:-}" && ${#GAME_AUTH_JWT_SECRET} -lt 32 ]]; then
  echo "FAIL: GAME_AUTH_JWT_SECRET must be ≥ 32 chars"
  fail=1
fi
if [[ -n "${GAME_ADMIN_TOKEN:-}" && ${#GAME_ADMIN_TOKEN} -lt 24 ]]; then
  echo "FAIL: GAME_ADMIN_TOKEN must be ≥ 24 chars"
  fail=1
fi
if [[ -n "${GAME_METRICS_SCRAPE_TOKEN:-}" && ${#GAME_METRICS_SCRAPE_TOKEN} -lt 16 ]]; then
  echo "FAIL: GAME_METRICS_SCRAPE_TOKEN must be ≥ 16 chars"
  fail=1
fi
if [[ "${GAME_CORS_ALLOWED_ORIGINS:-}" == "*" ]] || [[ "${GAME_CORS_ALLOWED_ORIGINS:-}" == *'*'* ]]; then
  echo "FAIL: GAME_CORS_ALLOWED_ORIGINS must not contain wildcards"
  fail=1
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "OK: $ENV_FILE looks deployable (guard-critical keys present)"
