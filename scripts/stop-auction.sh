#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/auction-common.sh"

export POSTGRES_VOLUME_NAME="backend_pgdata"
export PUBLIC_BIND_ADDRESS="0.0.0.0"
export PUBLIC_HTTP_PORT="8088"

echo "Arresto stack ASTA senza cancellare il volume PostgreSQL..."
auction_compose down

echo "Riavvio il solo PostgreSQL di sviluppo sullo stesso volume..."
docker compose --env-file "$AUCTION_DB_ENV" -f "$AUCTION_ROOT/backend/docker-compose.yml" up -d postgres
echo "Stack asta fermato. Database conservato."
