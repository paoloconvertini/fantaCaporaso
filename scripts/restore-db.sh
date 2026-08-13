#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || "$1" != "--confirm" ]]; then
  echo "Uso: $0 --confirm <backup.dump> [env-file]" >&2
  echo "ATTENZIONE: il database applicativo corrente verra' sostituito." >&2
  exit 1
fi

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backup_file="$2"
env_file="${3:-$repo_dir/config/application-prod.local.env}"

if [[ ! -s "$backup_file" || ! -f "$env_file" ]]; then
  echo "Backup o file env non valido." >&2
  exit 1
fi

docker compose --env-file "$env_file" -f "$repo_dir/docker-compose.prod.yml" stop backend
docker compose --env-file "$env_file" -f "$repo_dir/docker-compose.prod.yml" exec -T postgres \
  sh -c 'dropdb -U "$POSTGRES_USER" --if-exists "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
docker compose --env-file "$env_file" -f "$repo_dir/docker-compose.prod.yml" exec -T postgres \
  sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < "$backup_file"
docker compose --env-file "$env_file" -f "$repo_dir/docker-compose.prod.yml" start backend

echo "Ripristino completato da: $backup_file"
