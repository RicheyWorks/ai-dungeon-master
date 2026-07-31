#!/usr/bin/env bash
# Build the Vite SPA with base=/app/ and stage it into the Spring Boot static tree
# so the fat jar serves the full client at http://host:8080/app/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/web"

if [[ ! -d node_modules ]]; then
  npm install
fi

echo "[build-web] building with VITE_BASE=/app/"
VITE_BASE=/app/ npm run build

DEST="$ROOT/service/src/main/resources/static/app"
rm -rf "$DEST"
mkdir -p "$DEST"
cp -a dist/. "$DEST/"

# Marker so tests / packaging can assert the SPA is present.
cat > "$DEST/.built-from" <<EOF
web SPA staged $(date -u +%Y-%m-%dT%H:%M:%SZ)
base=/app/
EOF

echo "[build-web] staged → $DEST"
ls -la "$DEST"
