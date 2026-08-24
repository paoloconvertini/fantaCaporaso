#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/dev-common.sh"
source "$DEV_ROOT/scripts/auction-common.sh"

dev_assert_volume_separation
auction_assert_database
dev_assert_database

dev_tables="$(dev_compose exec -T postgres sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select count(*) from information_schema.tables where table_schema = '\''public'\''"')"
if [[ "$dev_tables" != "0" ]]; then
  echo "BLOCCO: il database dev contiene gia' tabelle. La clonazione iniziale non verra' sovrascritta." >&2
  exit 1
fi

dump_file="$(mktemp "${TMPDIR:-/tmp}/fantasta-prod-clone.XXXXXX.dump")"
trap 'rm -f "$dump_file"' EXIT
auction_compose exec -T postgres sh -lc 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$dump_file"
auction_compose exec -T postgres pg_restore -l < "$dump_file" >/dev/null
dev_compose exec -T postgres sh -lc 'pg_restore --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$dump_file"

prod_counts="$(auction_database_counts)"
dev_counts="$(dev_compose exec -T postgres sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "select (select count(*) from participant) || '\''|'\'' || (select count(*) from player) || '\''|'\'' || (select count(*) from rosters)"')"
if [[ "$dev_counts" != "$prod_counts" ]]; then
  echo "BLOCCO: conteggi diversi dopo il clone (prod=$prod_counts, dev=$dev_counts)." >&2
  exit 1
fi

echo "Clone iniziale completato e verificato: partecipanti|calciatori|rose=$dev_counts"
echo "Da questo momento i due database sono indipendenti."
