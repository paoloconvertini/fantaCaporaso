#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/config/application-cloud.env"
PROJECT_NAME="fantacaporaso-gazzetta"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "File PROD non trovato: $ENV_FILE" >&2
  exit 1
fi

if ! grep -q '^CLOUDFLARE_ACCOUNT_ID=.' "$ENV_FILE" \
    || ! grep -q '^CLOUDFLARE_PAGES_API_TOKEN=.' "$ENV_FILE"; then
  echo "Account ID o token Cloudflare Pages mancanti in $ENV_FILE" >&2
  exit 1
fi

temporary_file="$(mktemp "${TMPDIR:-/tmp}/fantacaporaso-gazzetta.XXXXXX")"
cleanup() { rm -f "$temporary_file"; }
trap cleanup EXIT

while IFS= read -r line || [[ -n "$line" ]]; do
  case "$line" in
    NEWSPAPER_PUBLISH_ENABLED=*|CLOUDFLARE_GAZZETTA_PROJECT=*) ;;
    *) printf '%s\n' "$line" >> "$temporary_file" ;;
  esac
done < "$ENV_FILE"

{
  printf '\n# Prima pagina Gazzetta (progetto Pages separato)\n'
  printf 'NEWSPAPER_PUBLISH_ENABLED=true\n'
  printf 'CLOUDFLARE_GAZZETTA_PROJECT=%s\n' "$PROJECT_NAME"
} >> "$temporary_file"

chmod 600 "$temporary_file"
mv "$temporary_file" "$ENV_FILE"
trap - EXIT

echo "Pubblicazione Gazzetta configurata sul progetto $PROJECT_NAME."
