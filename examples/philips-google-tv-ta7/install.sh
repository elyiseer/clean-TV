#!/usr/bin/env bash
# Build the launcher, install it on the Philips Google TV TA7 as Home and push this TV's theme.
# Safe to run again after editing theme/config.json or the launcher source.
#   1. disables the setup wizard (its RecoveryActivity otherwise takes Home), unless it owns a saved
#      Wi-Fi network: Android deletes that network when the wizard is disabled
#   2. launcher/build-and-install.sh with HOME_KEY_SERVICE=1, so the remote's gear button opens
#      Settings; it disables the stock launcher on the first run
#   3. tools/theme.sh with theme/ (dock order, greeting, hidden apps)
#
# Usage: TV_HOST=192.168.0.10 examples/philips-google-tv-ta7/install.sh
#        FORCE_SETUPWRAITH=1 skips the Wi-Fi check (the network the wizard owns is one you don't use)
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.0.10 $0}"
export TV_HOST
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
PY="$ROOT/tools/venv/bin/python"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
tv() { "$PY" -I "$ROOT/tools/tv.py" "$@"; }

SETUP=com.google.android.tungsten.setupwraith
if tv "pm list packages -e $SETUP" | grep -q "$SETUP"; then
  if tv "dumpsys wifi" | grep -o 'cname=[^ ]*' | grep -q setupwraith && [ -z "${FORCE_SETUPWRAITH:-}" ]; then
    echo "The setup wizard owns a saved Wi-Fi network, and disabling it deletes that network." >&2
    echo "If that is the network the TV is on, forget it and join it again in Settings > Network first." >&2
    echo "If it is an old network you don't use, run again with FORCE_SETUPWRAITH=1." >&2
    exit 1
  fi
  tv "pm disable-user --user 0 $SETUP"
fi

# The stock launcher is disabled on the first run; later runs find Home already ours and leave it.
STOCK=none
if tv "pm list packages -e com.google.android.apps.tv.launcherx" | grep -q launcherx; then
  STOCK=com.google.android.apps.tv.launcherx
fi
STOCK_LAUNCHER=$STOCK HOME_KEY_SERVICE=1 "$ROOT/launcher/build-and-install.sh"
"$ROOT/tools/theme.sh" "$HERE/theme"
