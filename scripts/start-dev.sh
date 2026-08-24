#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/dev-common.sh"

dev_assert_volume_separation
docker volume inspect "$DEV_POSTGRES_VOLUME" >/dev/null 2>&1 || docker volume create "$DEV_POSTGRES_VOLUME" >/dev/null
dev_compose up -d --wait postgres
dev_assert_database

echo "Sviluppo pronto: PostgreSQL su localhost:5432 (database fantasta_dev)."
echo "Avvia ora 'Backend - Quarkus Dev' e 'Frontend - Angular Dev' da IntelliJ."
