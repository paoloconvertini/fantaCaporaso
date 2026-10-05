#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/auction-common.sh"
auction_assert_database >&2
auction_compose exec -T postgres sh -lc 'psql -v ON_ERROR_STOP=1 -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "BEGIN TRANSACTION READ ONLY; SELECT json_agg(snapshot ORDER BY team, role, player) FROM (SELECT p.name AS team, pl.name AS player, pl.team AS club, pl.role AS role FROM rosters r JOIN participant p ON p.id = r.participant_id JOIN player pl ON pl.id = r.player_id) snapshot; COMMIT;"' \
  | python3 "$AUCTION_ROOT/scripts/render-public-rosters.py" "$AUCTION_ROOT/landing-page/rose"
