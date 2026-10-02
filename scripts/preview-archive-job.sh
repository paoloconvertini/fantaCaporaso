#!/usr/bin/env bash
set -euo pipefail
ARCHIVE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ "${1:-}" == "--review" ]]; then
  ARCHIVE_PREVIEW="$ARCHIVE_ROOT/.local/archive-job/review-preview"
  echo "ANTEPRIMA DA VERIFICARE: consulta il rapporto, non è una versione pronta alla pubblicazione."
elif [[ $# -eq 0 ]]; then
  ARCHIVE_PREVIEW="$ARCHIVE_ROOT/.local/archive-job/latest-preview"
else
  echo "Uso: $0 [--review]" >&2
  exit 2
fi
if [[ ! -f "$ARCHIVE_PREVIEW/index.html" ]]; then
  echo "Nessuna anteprima completa. Consulta .local/archive-job/last-run.json." >&2
  exit 1
fi
echo "Anteprima locale: http://127.0.0.1:8787 (Ctrl-C per fermarla)"
exec /usr/bin/python3 -m http.server 8787 --bind 127.0.0.1 --directory "$ARCHIVE_PREVIEW"
