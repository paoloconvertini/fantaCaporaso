#!/usr/bin/env bash

set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/dev-common.sh"

dev_assert_volume_separation
dev_compose stop postgres
echo "Database dev fermato. Il volume $DEV_POSTGRES_VOLUME e' stato conservato."
