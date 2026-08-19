#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/auction-common.sh"

export POSTGRES_VOLUME_NAME="$AUCTION_POSTGRES_VOLUME"
export PUBLIC_BIND_ADDRESS="0.0.0.0"
export PUBLIC_HTTP_PORT="8088"

echo "Arresto soltanto i servizi applicativi; PostgreSQL resta attivo e non viene toccato..."
auction_compose stop cloudflared reverse-proxy frontend backend
echo "Stack applicativo fermato. Database ancora attivo sul volume $AUCTION_POSTGRES_VOLUME."
