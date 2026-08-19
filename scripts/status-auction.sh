#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/auction-common.sh"

export POSTGRES_VOLUME_NAME="$AUCTION_POSTGRES_VOLUME"
export PUBLIC_BIND_ADDRESS="0.0.0.0"
export PUBLIC_HTTP_PORT="8088"

auction_print_links
auction_compose ps
auction_assert_database

public_url="$(auction_public_url)"
auction_compose exec -T backend curl --fail --silent --max-time 5 http://127.0.0.1:8080/q/health/ready >/dev/null
echo "Backend locale: OK"

if [[ -n "$public_url" ]] && curl --fail --silent --max-time 8 "$public_url/" >/dev/null; then
  echo "Link pubblico: OK"
else
  echo "Link pubblico: NON RAGGIUNGIBILE" >&2
  exit 1
fi
