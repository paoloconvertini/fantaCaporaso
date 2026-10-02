#!/usr/bin/env bash
set -euo pipefail
ARCHIVE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export PATH="$HOME/.volta/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
exec /usr/bin/python3 "$ARCHIVE_ROOT/scripts/archive-job/run.py" "$@"
