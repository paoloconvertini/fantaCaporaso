#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/auction-common.sh"
auction_assert_database >&2
auction_compose exec -T postgres sh -lc 'psql -v ON_ERROR_STOP=1 -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "BEGIN TRANSACTION READ ONLY; SELECT json_agg(snapshot ORDER BY name, club, role) FROM (SELECT pl.name, pl.team AS club, pl.role, p.name AS owner FROM player pl LEFT JOIN rosters r ON r.player_id=pl.id LEFT JOIN participant p ON p.id=r.participant_id WHERE pl.active=true OR r.id IS NOT NULL) snapshot; COMMIT;"' \
  | python3 "$AUCTION_ROOT/scripts/render-public-players.py" "$AUCTION_ROOT/landing-page/players.json"
