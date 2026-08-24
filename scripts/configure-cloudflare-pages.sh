#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/config/application-cloud.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "File PROD non trovato: $ENV_FILE" >&2
  exit 1
fi

read -r -p "Cloudflare Account ID: " account_id
read -r -s -p "Cloudflare Pages API token (input nascosto): " api_token
echo

if [[ -z "$account_id" || -z "$api_token" ]]; then
  echo "Account ID e token sono obbligatori." >&2
  exit 1
fi

temporary_file="$(mktemp "${TMPDIR:-/tmp}/fantacaporaso-cloudflare.XXXXXX")"
cleanup() { rm -f "$temporary_file"; }
trap cleanup EXIT

while IFS= read -r line || [[ -n "$line" ]]; do
  case "$line" in
    APP_ENVIRONMENT=*|AUCTION_ARCHIVE_PUBLISH_ENABLED=*|CLOUDFLARE_ACCOUNT_ID=*|CLOUDFLARE_PAGES_API_TOKEN=*|CLOUDFLARE_PAGES_PROJECT=*) ;;
    *) printf '%s\n' "$line" >> "$temporary_file" ;;
  esac
done < "$ENV_FILE"

{
  printf '\n# Archivio pubblico storico puntate (solo PROD)\n'
  printf 'APP_ENVIRONMENT=prod\n'
  printf 'AUCTION_ARCHIVE_PUBLISH_ENABLED=true\n'
  printf 'CLOUDFLARE_ACCOUNT_ID=%s\n' "$account_id"
  printf 'CLOUDFLARE_PAGES_API_TOKEN=%s\n' "$api_token"
  printf 'CLOUDFLARE_PAGES_PROJECT=fantacaporaso\n'
} >> "$temporary_file"

chmod 600 "$temporary_file"
mv "$temporary_file" "$ENV_FILE"
trap - EXIT

echo "Configurazione Cloudflare Pages salvata in config/application-cloud.env (file escluso da Git)."
