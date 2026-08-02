#!/usr/bin/env sh
# Emit strong random values for deploy/.env (stdout). Does not write files.
#   ./scripts/gen-secrets.sh >> deploy/.env
# Still set GAME_CORS_ALLOWED_ORIGINS and DOMAIN by hand after generating.
set -eu

rand() {
  # 48 bytes → ~64 base64 chars, URL-safe-ish
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48 | tr -d '\n' | tr '+/' '-_'
  else
    head -c 48 /dev/urandom | base64 | tr -d '\n' | tr '+/' '-_'
  fi
}

echo "# generated $(date -u +%Y-%m-%dT%H:%MZ)"
echo "POSTGRES_PASSWORD=$(rand)"
echo "GAME_AUTH_JDBC_PASSWORD=$(rand)"
echo "GAME_AUTH_JWT_SECRET=$(rand)"
echo "GAME_ADMIN_TOKEN=$(rand)"
echo "STOREFRONT_DEV_SECRET=$(rand)"
echo "STOREFRONT_GOOGLE_SECRET=$(rand)"
echo "STOREFRONT_APPLE_SECRET=$(rand)"
echo "STOREFRONT_STEAM_SECRET=$(rand)"
echo "GAME_PRODUCTION=true"
echo "SPRING_PROFILES_ACTIVE=prod"
echo "GAME_AUTH_ENABLED=true"
echo "GAME_RATE_LIMIT_STORE=redis"
echo "GAME_RATE_LIMIT_TRUST_FORWARDED_HEADERS=true"
echo "GAME_LEGACY_API_ENABLED=false"
echo "# REQUIRED: set explicit origins (no wildcards), e.g.:"
echo "# GAME_CORS_ALLOWED_ORIGINS=https://dm.example.com"
