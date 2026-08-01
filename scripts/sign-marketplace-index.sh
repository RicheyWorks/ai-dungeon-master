#!/usr/bin/env sh
# Prefer HTTP header signing of the exact response body:
#   X-Marketplace-Signature: sha256=$(openssl dgst -sha256 -hmac "$SECRET" index.json | awk '{print $2}')
#
# Embedded JSON "signature" is supported but depends on Jackson re-serialization
# after stripping the field — use the header form for production CDNs.
set -eu
FILE="${1:?path to index.json}"
: "${MARKETPLACE_HMAC_SECRET:?set MARKETPLACE_HMAC_SECRET}"
SIG=$(openssl dgst -sha256 -hmac "$MARKETPLACE_HMAC_SECRET" "$FILE" | awk '{print $2}')
echo "X-Marketplace-Signature: sha256=${SIG}"
echo "(sign the exact bytes of ${FILE}; serve that file unchanged)"
