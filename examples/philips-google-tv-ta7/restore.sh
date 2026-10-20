#!/usr/bin/env bash
# Undo everything done to the Philips Google TV TA7 from this folder, without needing the recovery
# record that tools/disable.sh keeps in ~/.cleanyourtv (tools/restore.sh uses that one instead):
#   - switches off the launcher's HomeKeyService (the gear button fix)
#   - re-enables every package in debloat.txt, the stock launcher and setup wizard included
#   - gives Home back to the stock Google TV launcher
#   - puts Private DNS back to automatic (AdGuard off)
# Packages the owner had disabled before (see the debloat.txt header) stay disabled.
# UNINSTALL_LAUNCHER=1 also removes the new launcher (com.gathin.tvhome).
#
# Usage: TV_HOST=192.168.0.10 examples/philips-google-tv-ta7/restore.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.0.10 $0}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
PY="$ROOT/tools/venv/bin/python"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
STOCK=com.google.android.apps.tv.launcherx
PKGS=$(grep -vE '^\s*(#|$)' "$HERE/debloat.txt" | sed -E 's/\s+#.*$//' | awk '{print $1}')

# One command per line: tools/tv.py --batch runs each as its own shell call, so a failure in one
# cannot silently skip the rest.
{
  echo "settings put secure enabled_accessibility_services \"\$(settings get secure enabled_accessibility_services | tr ':' '\n' | grep -v com.gathin.tvhome | paste -sd: -)\""
  # The stock launcher and the setup wizard first, so Home has an owner before anything else changes.
  echo "pm enable --user 0 com.google.android.tungsten.setupwraith"
  echo "pm enable --user 0 $STOCK"
  echo "cmd package set-home-activity --user 0 $STOCK/.home.HomeActivity"
  printf '%s\n' "$PKGS" | grep -vxF -e "$STOCK" -e com.google.android.tungsten.setupwraith | sed 's/^/pm enable --user 0 /'
  echo "settings put global private_dns_mode opportunistic"
  if [ "${UNINSTALL_LAUNCHER:-0}" = 1 ]; then echo "pm uninstall com.gathin.tvhome"; fi
  echo "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
} | TV_HOST="$TV_HOST" "$PY" -I "$ROOT/tools/tv.py" --batch
echo "Restored. Press Home on the remote: the stock Google TV home should come back."
