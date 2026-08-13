#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${1:-$repo_dir/config/application-prod.local.env}"
backup_dir="$repo_dir/backups"

if [[ ! -f "$env_file" ]]; then
  echo "File env non trovato: $env_file" >&2
  exit 1
fi

mkdir -p "$backup_dir"
backup_file="$backup_dir/fantasta-$(date +%Y%m%d-%H%M%S).dump"

docker compose --env-file "$env_file" -f "$repo_dir/docker-compose.prod.yml" exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup_file"

test -s "$backup_file"
echo "Backup creato: $backup_file"
