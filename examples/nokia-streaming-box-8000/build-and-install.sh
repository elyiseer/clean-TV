#!/usr/bin/env bash
# Build the launcher, install it on the Nokia Streaming Box 8000 and make it the home screen.
#
# This firmware gives com.google.android.tvlauncher HOME priority 2 and refuses to disable or uninstall
# it ("core application"), so `set-home-activity` alone cannot win. Instead the launcher's
# HomeKeyService is switched on with home_redirect_package (in theme/config.json): the Home button
# opens the new launcher, and it steps in front of the stock one whenever that comes up.
#
# Usage: TV_HOST=192.168.1.50 examples/nokia-streaming-box-8000/build-and-install.sh
#        THEME=<your theme folder> to push your own dock instead of theme/ (a dock_apps change drops the
#        order arranged on the TV, so keep pushing the same one)
# Needs JDK 17 or 21 (JAVA_HOME) and the Android SDK (ANDROID_HOME, default ~/Android/Sdk).
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$HERE/../.."
: "${TV_HOST:?set TV_HOST to the box IP}"
export TV_HOST
[ -d "${JAVA_HOME:-}" ] || { [ -d "$HOME/.local/jdk-21" ] && export JAVA_HOME="$HOME/.local/jdk-21"; }
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
PY="$ROOT/tools/venv/bin/python"
tv() { "$PY" -I "$ROOT/tools/tv.py" "$@"; }

# 1. Build and install. STOCK_LAUNCHER=none: the firmware refuses pm disable-user anyway. The script's
#    final "Home resolves to ... tvlauncher" error is expected here; only a failed install is fatal.
OUT=$(STOCK_LAUNCHER=none "$ROOT/launcher/build-and-install.sh" 2>&1); echo "$OUT" | grep -vE '^Hinweis|^Note:|chunk'
echo "$OUT" | grep -q '^Success' || { echo "ERROR: install failed, see above" >&2; exit 1; }

# 2. Theme first (dock, hidden apps, home redirect, network pill), so the service reads it when it binds.
"$ROOT/tools/theme.sh" "${THEME:-$HERE/theme}"

# 3. HomeKeyService on, without dropping other accessibility services, then check it is bound.
tv 's=$(settings get secure enabled_accessibility_services); case "$s" in *HomeKeyService*) ;; null|"") settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService ;; *) settings put secure enabled_accessibility_services "$s:com.gathin.tvhome/.HomeKeyService" ;; esac; settings put secure accessibility_enabled 1'
sleep 4
tv 'dumpsys accessibility' | grep -q 'Bound services:{Service\[label=' \
  && echo "HomeKeyService bound." || echo "WARNING: HomeKeyService is not bound yet; check docs/TROUBLESHOOTING.md" >&2

# 4. Screensaver: the launcher's Clock (the stock ambient backdrop is suspended by apply.sh).
tv 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream; settings put secure screensaver_default_component com.gathin.tvhome/.ClockDream'

# 5. Check: bring up the stock home and see who ends up in front.
tv 'am start -n com.google.android.tvlauncher/.MainActivity >/dev/null 2>&1; sleep 3; dumpsys activity activities | grep -m1 topResumedActivity'
