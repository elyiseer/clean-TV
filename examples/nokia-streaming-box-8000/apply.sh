#!/usr/bin/env bash
# Nokia Streaming Box 8000: this firmware answers every `pm disable*` with "Warning! This command is
# illegal!" and refuses `pm hide`, so tools/disable.sh cannot work here. Instead each package in
# debloat.txt is suspended (cannot be opened, no notifications), barred from running in the
# background, and stopped. Nothing is disabled or uninstalled; restore.sh undoes all of it.
#
# Usage: TV_HOST=192.168.1.50 examples/nokia-streaming-box-8000/apply.sh [batch-file]
#        (default: the whole debloat.txt next to this script)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
LIST="${1:-$HERE/debloat.txt}"
cd "$HERE/../.."
PY=tools/venv/bin/python
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }
: "${TV_HOST:?set TV_HOST to the box IP}"
PKGS=$(grep -vE '^\s*(#|$)' "$LIST" | sed -E 's/\s+#.*$//' | awk '{print $1}')
BAD=$(printf '%s\n' "$PKGS" | grep -vE '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' || true)
[ -z "$BAD" ] || { echo "refusing $LIST: not a package name: $BAD" >&2; exit 1; }
# Record what is there before the first change (first record wins, like tools/disable.sh).
"$PY" -I tools/recovery.py --save "$HERE/debloat.txt" >/dev/null 2>&1 || true
# One command per line; tv.py --batch runs each as its own shell call on one connection.
for p in $PKGS; do
  echo "pm suspend --user 0 $p"
  echo "cmd appops set $p RUN_ANY_IN_BACKGROUND ignore"
  echo "am force-stop $p"
done | "$PY" -I tools/tv.py --batch
