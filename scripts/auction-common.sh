#!/usr/bin/env bash

set -euo pipefail

AUCTION_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AUCTION_PROJECT="fantasta-auction"
AUCTION_LOCAL_URL="http://localhost:8088"
AUCTION_CLOUD_ENV="$AUCTION_ROOT/config/application-cloud.env"
AUCTION_DB_ENV="$AUCTION_ROOT/backend/.env"

if [[ ! -f "$AUCTION_CLOUD_ENV" || ! -f "$AUCTION_DB_ENV" ]]; then
  echo "Configurazione mancante: servono config/application-cloud.env e backend/.env" >&2
  exit 1
fi

auction_compose() {
  docker compose \
    --project-name "$AUCTION_PROJECT" \
    --env-file "$AUCTION_CLOUD_ENV" \
    --env-file "$AUCTION_DB_ENV" \
    -f "$AUCTION_ROOT/docker-compose.prod.yml" \
    -f "$AUCTION_ROOT/docker-compose.cloud.yml" \
    "$@"
}

auction_public_url() {
  auction_compose logs --no-color cloudflared 2>/dev/null \
    | sed -nE 's#.*(https://[a-zA-Z0-9-]+\.trycloudflare\.com).*#\1#p' \
    | tail -n 1
}

auction_lan_url() {
  local address=""
  address="$(ipconfig getifaddr en0 2>/dev/null || true)"
  [[ -n "$address" ]] || address="$(ipconfig getifaddr en1 2>/dev/null || true)"
  [[ -n "$address" ]] && echo "http://$address:8088"
}

auction_print_links() {
  local public_url lan_url
  public_url="$(auction_public_url)"
  lan_url="$(auction_lan_url)"

  echo
  echo "============================================================"
  echo " LINK DA INVIARE AI PARTECIPANTI"
  echo " ${public_url:-TUNNEL NON ANCORA DISPONIBILE}"
  echo "============================================================"
  echo " Link gestore sul Mac: $AUCTION_LOCAL_URL"
  [[ -n "$lan_url" ]] && echo " Link di emergenza sulla stessa Wi-Fi: $lan_url"
  echo "============================================================"
  echo
}
