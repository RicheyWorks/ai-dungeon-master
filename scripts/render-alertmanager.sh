#!/usr/bin/env sh
# Render deploy/alertmanager/alertmanager.receivers.yml with secrets from env.
# Writes deploy/alertmanager/alertmanager.active.yml (gitignored).
#
#   export SLACK_WEBHOOK_URL='https://hooks.slack.com/services/…'
#   export SLACK_CHANNEL='#dm-alerts'
#   export PAGERDUTY_ROUTING_KEY='…'   # Events API v2
#   ./scripts/render-alertmanager.sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
SRC="${ROOT}/deploy/alertmanager/alertmanager.receivers.yml"
OUT="${ROOT}/deploy/alertmanager/alertmanager.active.yml"

: "${SLACK_WEBHOOK_URL:?set SLACK_WEBHOOK_URL}"
: "${SLACK_CHANNEL:=#dm-alerts}"
: "${PAGERDUTY_ROUTING_KEY:?set PAGERDUTY_ROUTING_KEY}"

# Escape sed replacement specials in secrets
esc() {
  printf '%s' "$1" | sed -e 's/[\\/&]/\\&/g'
}

sed \
  -e "s|__SLACK_WEBHOOK_URL__|$(esc "$SLACK_WEBHOOK_URL")|g" \
  -e "s|__SLACK_CHANNEL__|$(esc "$SLACK_CHANNEL")|g" \
  -e "s|__PAGERDUTY_ROUTING_KEY__|$(esc "$PAGERDUTY_ROUTING_KEY")|g" \
  "$SRC" > "$OUT"

echo "wrote $OUT"
echo "Use with: ALERTMANAGER_CONFIG=alertmanager.active.yml"
echo "  docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.metrics.yml up -d alertmanager"
