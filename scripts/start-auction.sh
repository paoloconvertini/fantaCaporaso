#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/auction-common.sh"

echo "Avvio modalità ASTA usando il volume persistente backend_pgdata..."
docker info >/dev/null

# Lo stesso volume PostgreSQL non deve essere aperto da due server contemporaneamente.
docker compose --env-file "$AUCTION_DB_ENV" -f "$AUCTION_ROOT/backend/docker-compose.yml" stop postgres >/dev/null 2>&1 || true

export POSTGRES_VOLUME_NAME="backend_pgdata"
export PUBLIC_BIND_ADDRESS="0.0.0.0"
export PUBLIC_HTTP_PORT="8088"

auction_compose up -d --build

echo "Attendo frontend, backend e database..."
ready=false
for _ in $(seq 1 90); do
  if curl --fail --silent --max-time 2 "$AUCTION_LOCAL_URL/" >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done

if [[ "$ready" != true ]]; then
  echo "Lo stack non è diventato pronto entro 180 secondi." >&2
  auction_compose ps
  exit 1
fi

echo "Attendo l'indirizzo pubblico Cloudflare..."
for _ in $(seq 1 60); do
  [[ -n "$(auction_public_url)" ]] && break
  sleep 2
done

public_url="$(auction_public_url)"
if [[ -z "$public_url" ]]; then
  echo "Cloudflare non ha generato il link pubblico." >&2
  auction_compose logs --tail=100 cloudflared
  exit 1
fi

public_ready=false
for _ in $(seq 1 30); do
  if curl --fail --silent --max-time 5 "$public_url/" >/dev/null 2>&1; then
    public_ready=true
    break
  fi
  sleep 2
done

auction_print_links
auction_compose ps

if [[ "$public_ready" != true ]]; then
  echo "ATTENZIONE: link generato ma verifica HTTPS non ancora riuscita; riprova ASTA - STATO tra pochi secondi." >&2
  exit 1
fi

echo "Modalità ASTA pronta. Non chiudere Docker Desktop e non sospendere il Mac."
