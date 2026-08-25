#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/auction-common.sh"

echo "Avvio modalità ASTA usando il volume persistente backend_pgdata..."
docker info >/dev/null

rebuild=false
open_path="/"
for argument in "$@"; do
  case "$argument" in
    --rebuild) rebuild=true ;;
    --gazzetta) open_path="/admin/gazzetta" ;;
    *)
      echo "Uso: $0 [--rebuild] [--gazzetta]" >&2
      exit 2
      ;;
  esac
done

export POSTGRES_VOLUME_NAME="$AUCTION_POSTGRES_VOLUME"
export PUBLIC_BIND_ADDRESS="0.0.0.0"
export PUBLIC_HTTP_PORT="8088"

auction_service_running() {
  local container
  container="$(auction_compose ps -q "$1")"
  [[ -n "$container" ]] && [[ "$(docker inspect -f '{{.State.Running}}' "$container")" == "true" ]]
}

auction_service_healthy() {
  local container
  container="$(auction_compose ps -q "$1")"
  [[ -n "$container" ]] \
    && [[ "$(docker inspect -f '{{.State.Running}}' "$container")" == "true" ]] \
    && [[ "$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container")" == "healthy" ]]
}

if [[ "$rebuild" != true ]] \
  && auction_service_healthy postgres \
  && auction_service_healthy backend \
  && auction_service_healthy frontend \
  && auction_service_healthy reverse-proxy \
  && auction_service_running cloudflared \
  && curl --fail --silent --max-time 2 "$AUCTION_LOCAL_URL/" >/dev/null 2>&1 \
  && curl --fail --silent --max-time 5 "$(auction_public_url)/" >/dev/null 2>&1; then
  auction_assert_database
  auction_print_links
  auction_compose ps
  auction_open_in_chrome "$AUCTION_LOCAL_URL$open_path"
  echo "PROD è già attivo e raggiungibile. Nessun container è stato riavviato o ricreato."
  exit 0
fi

if [[ -z "$(auction_postgres_container)" ]]; then
  echo "Avvio il database persistente; i deploy successivi non lo toccheranno..."
  auction_compose up -d postgres
fi
auction_assert_database

if [[ "$rebuild" == true ]]; then
  "$AUCTION_ROOT/scripts/deploy-auction.sh" --rebuild
fi

auction_compose up -d --no-deps backend frontend reverse-proxy

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
public_url="$(auction_public_url)"
echo "Avvio Named Tunnel Cloudflare su $public_url..."
auction_compose up -d --no-deps cloudflared

for _ in $(seq 1 90); do
  if curl --fail --silent --max-time 5 "$public_url/" >/dev/null 2>&1; then
    public_ready=true
    break
  fi
  sleep 2
done

auction_print_links
auction_compose ps

if [[ "$public_ready" != true ]]; then
  echo "Named Tunnel non raggiungibile su $public_url." >&2
  auction_compose logs --tail=100 cloudflared
  exit 1
fi

auction_open_in_chrome "$AUCTION_LOCAL_URL$open_path"
echo "Modalità ASTA pronta. Non chiudere Docker Desktop e non sospendere il Mac."
