#!/usr/bin/env bash

set -euo pipefail

AUCTION_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AUCTION_PROJECT="fantasta-auction"
AUCTION_LOCAL_URL="http://localhost:8088"
AUCTION_PUBLIC_URL="https://asta.fantacaporaso.it"
AUCTION_CLOUD_ENV="$AUCTION_ROOT/config/application-cloud.env"
AUCTION_DB_ENV="$AUCTION_ROOT/backend/.env"
AUCTION_POSTGRES_VOLUME="backend_pgdata"

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

auction_postgres_container() {
  auction_compose ps -q postgres
}

auction_assert_database() {
  local container mounted_volume counts
  container="$(auction_postgres_container)"
  if [[ -z "$container" ]] || [[ "$(docker inspect -f '{{.State.Running}}' "$container")" != "true" ]]; then
    echo "Database d'asta non attivo." >&2
    return 1
  fi

  mounted_volume="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/var/lib/postgresql/data"}}{{.Name}}{{end}}{{end}}' "$container")"
  if [[ "$mounted_volume" != "$AUCTION_POSTGRES_VOLUME" ]]; then
    echo "BLOCCO: PostgreSQL usa il volume '$mounted_volume', atteso '$AUCTION_POSTGRES_VOLUME'." >&2
    return 1
  fi

  counts="$(auction_compose exec -T postgres sh -lc \
    'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select (select count(*) from participant) || '\''|'\'' || (select count(*) from player) || '\''|'\'' || (select count(*) from rosters)"')"
  IFS='|' read -r participants players rosters <<< "$counts"
  if (( participants < 1 || players < 1 || rosters < 1 )); then
    echo "BLOCCO: database inaspettatamente vuoto (partecipanti=$participants, calciatori=$players, rose=$rosters)." >&2
    return 1
  fi
  echo "Database verificato: volume=$mounted_volume, partecipanti=$participants, calciatori=$players, rose=$rosters"
}

auction_assert_deploy_allowed() {
  local blocked
  blocked="$(auction_compose exec -T postgres sh -lc \
    'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select case when exists (select 1 from mercato_config where attiva = true and (finesessione is null or finesessione > localtimestamp)) or exists (select 1 from auction_round_state where convert_from(lo_get(statejson), '\''UTF8'\'')::jsonb ->> '\''closed'\'' = '\''false'\'') then 1 else 0 end"')"
  if [[ "$blocked" == "1" ]]; then
    echo "BLOCCO: deploy vietato con mercato o round attivo." >&2
    return 1
  fi
}

auction_database_counts() {
  auction_compose exec -T postgres sh -lc \
    'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select (select count(*) from participant) || '\''|'\'' || (select count(*) from player) || '\''|'\'' || (select count(*) from rosters)"'
}

auction_backup_database() {
  local backup_file
  mkdir -p "$AUCTION_ROOT/backups"
  backup_file="$AUCTION_ROOT/backups/fantasta-before-deploy-$(date +%Y%m%d-%H%M%S).dump"
  auction_compose exec -T postgres sh -lc \
    'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup_file"
  if [[ ! -s "$backup_file" ]]; then
    echo "BLOCCO: backup non creato correttamente." >&2
    return 1
  fi
  auction_compose exec -T postgres pg_restore -l < "$backup_file" >/dev/null
  echo "Backup verificato: $backup_file"
}

auction_public_url() {
  echo "$AUCTION_PUBLIC_URL"
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
