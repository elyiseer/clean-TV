#!/usr/bin/env bash
# Put the Nokia Streaming Box 8000 back the way it was before apply.sh and build-and-install.sh:
#   - unsuspend every package in debloat.txt and reset its app-ops to the defaults (all of them were
#     on the default "allow" and unsuspended before; see backup/.../appops-suspend-before.txt)
#   - switch the launcher's HomeKeyService off (accessibility was off with no services before), so the
#     stock Android TV home owns Home again; it never stopped being the system's Home
#   - screensaver back to the ambient backdrop, Private DNS back to Android's default (opportunistic)
# The new launcher stays installed but is no longer in the way; remove it with
#   TV_HOST=<ip> tools/tv.py 'pm uninstall com.gathin.tvhome'
#
# Usage: TV_HOST=192.168.1.50 examples/nokia-streaming-box-8000/restore.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE/../.."
PY=tools/venv/bin/python
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }
: "${TV_HOST:?set TV_HOST to the box IP}"
PKGS=$(grep -vE '^\s*(#|$)' "$HERE/debloat.txt" | sed -E 's/\s+#.*$//' | awk '{print $1}')
{
  for p in $PKGS com.google.android.tvlauncher; do
    echo "pm unsuspend --user 0 $p"
    echo "cmd appops reset --user 0 $p"
  done
  # the first probe on this box also set a distracting-app flag on Feedback
  echo "pm set-distracting-restriction --user 0 --flag none com.google.android.feedback"
  echo "settings delete secure enabled_accessibility_services"
  echo "settings put secure accessibility_enabled 0"
  echo "settings put secure screensaver_components com.google.android.backdrop/.Backdrop"
  echo "settings put secure screensaver_default_component com.google.android.backdrop/.Backdrop"
  echo "settings put global private_dns_mode opportunistic"
  echo "settings delete global private_dns_specifier"
  echo "cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity"
  echo "am start -a android.intent.action.MAIN -c android.intent.category.HOME"
  echo "pm list packages -d"
  echo "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
} | "$PY" -I tools/tv.py --batch
echo "Restored. Packages still listed as disabled above were disabled at the factory (com.nes.seilivetv, com.google.android.tv.axel)."
