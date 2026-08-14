#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/auction-common.sh"

echo "Avvio modalità ASTA usando il volume persistente backend_pgdata..."
docker info >/dev/null

rebuild=false
if [[ "${1:-}" == "--rebuild" ]]; then
  rebuild=true
elif [[ $# -gt 0 ]]; then
  echo "Uso: $0 [--rebuild]" >&2
  exit 2
fi

# Lo stesso volume PostgreSQL non deve essere aperto da due server contemporaneamente.
docker compose --env-file "$AUCTION_DB_ENV" -f "$AUCTION_ROOT/backend/docker-compose.yml" stop postgres >/dev/null 2>&1 || true

export POSTGRES_VOLUME_NAME="backend_pgdata"
export PUBLIC_BIND_ADDRESS="0.0.0.0"
export PUBLIC_HTTP_PORT="8088"

if [[ "$rebuild" == true ]]; then
  echo "Ricostruisco backend e frontend..."
  auction_compose build backend frontend
fi

# Il Quick Tunnel viene ricreato più sotto: un hostname precedente può essere scaduto.
auction_compose up -d postgres backend frontend reverse-proxy

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

public_ready=false
public_url=""
for tunnel_attempt in 1 2 3; do
  echo "Genero Quick Tunnel Cloudflare (tentativo $tunnel_attempt/3)..."
  auction_compose up -d --force-recreate --no-deps cloudflared

  public_url=""
  for _ in $(seq 1 45); do
    public_url="$(auction_public_url)"
    [[ -n "$public_url" ]] && break
    sleep 2
  done

  if [[ -z "$public_url" ]]; then
    echo "Cloudflare non ha ancora generato un hostname." >&2
    continue
  fi

  for _ in $(seq 1 30); do
    if curl --fail --silent --max-time 5 "$public_url/" >/dev/null 2>&1; then
      public_ready=true
      break
    fi
    sleep 2
  done

  if [[ "$public_ready" == true ]]; then
    break
  fi
  echo "Hostname non raggiungibile: $public_url. Lo rigenero." >&2
done

auction_print_links
auction_compose ps

if [[ "$public_ready" != true ]]; then
  echo "Cloudflare non ha prodotto un link HTTPS raggiungibile dopo 3 tentativi." >&2
  auction_compose logs --tail=100 cloudflared
  exit 1
fi

echo "Modalità ASTA pronta. Non chiudere Docker Desktop e non sospendere il Mac."
