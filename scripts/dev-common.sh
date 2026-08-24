#!/usr/bin/env bash

set -euo pipefail

DEV_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_PROJECT="fantasta-dev"
DEV_ENV="$DEV_ROOT/config/application-dev.env"
DEV_POSTGRES_VOLUME="fantasta_dev_pgdata"
PROD_POSTGRES_VOLUME="backend_pgdata"

if [[ ! -f "$DEV_ENV" ]]; then
  echo "Configurazione mancante: crea config/application-dev.env dal relativo esempio." >&2
  exit 1
fi

dev_compose() {
  docker compose --project-name "$DEV_PROJECT" --env-file "$DEV_ENV" -f "$DEV_ROOT/docker-compose.dev.yml" "$@"
}

dev_postgres_container() {
  dev_compose ps -q postgres
}

dev_assert_volume_separation() {
  if [[ "$DEV_POSTGRES_VOLUME" == "$PROD_POSTGRES_VOLUME" ]]; then
    echo "BLOCCO: i volumi dev e produzione coincidono." >&2
    return 1
  fi
  if ! dev_compose config | grep -q "name: $DEV_POSTGRES_VOLUME"; then
    echo "BLOCCO: Compose dev non dichiara il volume protetto $DEV_POSTGRES_VOLUME." >&2
    return 1
  fi
}

dev_assert_database() {
  local container mounted_volume database_name
  container="$(dev_postgres_container)"
  if [[ -z "$container" ]] || [[ "$(docker inspect -f '{{.State.Running}}' "$container")" != "true" ]]; then
    echo "Database di sviluppo non attivo." >&2
    return 1
  fi

  mounted_volume="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}' "$container")"
  if [[ "$mounted_volume" != "$DEV_POSTGRES_VOLUME" || "$mounted_volume" == "$PROD_POSTGRES_VOLUME" ]]; then
    echo "BLOCCO: PostgreSQL dev usa '$mounted_volume', atteso '$DEV_POSTGRES_VOLUME'." >&2
    return 1
  fi

  database_name="$(dev_compose exec -T postgres sh -lc 'printf "%s" "$POSTGRES_DB"')"
  if [[ "$database_name" != "fantasta_dev" ]]; then
    echo "BLOCCO: database dev '$database_name', atteso 'fantasta_dev'." >&2
    return 1
  fi
  echo "Database dev verificato: database=$database_name, volume=$mounted_volume"
}
