#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/auction-common.sh"

export POSTGRES_VOLUME_NAME="$AUCTION_POSTGRES_VOLUME"
export PUBLIC_BIND_ADDRESS="0.0.0.0"
export PUBLIC_HTTP_PORT="8088"

if [[ "${1:-}" == "--rebuild" ]]; then
  rebuild=true
elif [[ $# -eq 0 ]]; then
  rebuild=false
else
  echo "Uso: $0 [--rebuild]" >&2
  exit 2
fi

auction_assert_database
auction_assert_deploy_allowed

if [[ "$rebuild" == true ]]; then
  echo "Costruisco backend e frontend senza modificare PostgreSQL..."
  auction_compose build backend frontend
fi

# Ricontrolla dopo la build: nel frattempo potrebbe essere iniziato un round.
auction_assert_database
auction_assert_deploy_allowed
before_counts="$(auction_database_counts)"
auction_backup_database

echo "Distribuisco soltanto i servizi applicativi..."
auction_compose up -d --no-deps backend frontend reverse-proxy

ready=false
for _ in $(seq 1 90); do
  if curl --fail --silent --max-time 2 "$AUCTION_LOCAL_URL/" >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done
if [[ "$ready" != true ]]; then
  echo "Deploy non raggiungibile entro 180 secondi; PostgreSQL non è stato modificato." >&2
  exit 1
fi

auction_assert_database
after_counts="$(auction_database_counts)"
if [[ "$before_counts" != "$after_counts" ]]; then
  echo "BLOCCO: i conteggi del database sono cambiati durante il deploy ($before_counts -> $after_counts)." >&2
  exit 1
fi

echo "Deploy completato. PostgreSQL non è stato ricreato né riavviato."
