#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/auction-common.sh"
export POSTGRES_VOLUME_NAME="$AUCTION_POSTGRES_VOLUME"
export PUBLIC_BIND_ADDRESS="0.0.0.0"
export PUBLIC_HTTP_PORT="8088"
auction_assert_database
auction_assert_deploy_allowed
auction_compose stop backend frontend

docker compose --project-name "$AUCTION_PROJECT" \
  --env-file "$AUCTION_CLOUD_ENV" --env-file "$AUCTION_DB_ENV" \
  -f "$AUCTION_ROOT/docker-compose.prod.yml" \
  -f "$AUCTION_ROOT/docker-compose.cloud.yml" \
  -f "$AUCTION_ROOT/docker-compose.closed.yml" \
  up -d --no-deps reverse-proxy cloudflared

echo "Pagina asta chiusa attiva. Backend e frontend spenti; database conservato."
echo "Per riaprire: ./scripts/start-auction.sh"
