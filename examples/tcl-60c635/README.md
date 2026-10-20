# TCL 60C635 (Google TV 11)

Tested on a real unit on 2026-10-20, over Wi-Fi, in Spain.

| Field | Value |
|---|---|
| `ro.product.model` / `name` / `device` | `Smart TV Pro` / `G03_4K_GB` / `BeyondTV4` |
| Android / SDK | 11 / 30, security patch 2024-12-05 |
| Firmware | `V8-R51MT05-LF1V652`, `AR11` (same as `tcl-43c645` and `docs/devices/tcl-75c63b.md`) |
| SoC / RAM | Realtek `rtd288o`, 2 GB |
| Remote | `TCL_RC902V_U1` (Bluetooth, `Generic.kl`) |
| `ro.adb.secure` | 1 (the "Allow USB debugging?" prompt appears) |

Port 5555 was open with only **USB debugging** on; this firmware has no separate network-debugging switch.

## What is here

| File | What it does |
|---|---|
| `debloat.txt` | 36 packages in 6 batches: telemetry, TCL content/ads, background services, voice assistant, unused preloads, and the stock launcher handover |
| `config.json` | Launcher overlay for this TV: dock, Live TV app, remote buttons |
| `build-and-install.sh` | Builds `launcher/`, installs it, pushes `config.json`, makes it Home, disables launcherx and setupwraith, switches the key services on |
| `restore.sh` | Re-enables everything in `debloat.txt`, gives Home back to launcherx, switches the key services off (`--uninstall` also removes the launcher) |

```sh
TV_HOST=<tv ip> tools/disable.sh examples/tcl-60c635/debloat.txt   # or one batch at a time, see below
TV_HOST=<tv ip> examples/tcl-60c635/build-and-install.sh
TV_HOST=<tv ip> examples/tcl-60c635/restore.sh                     # undo
```

The scripts use the stock `adb` from platform-tools (`$ANDROID_HOME/platform-tools/adb`). On WSL2 the adb
daemon hung while forking; `adb -P 15037 nodaemon server` in another terminal plus `ADB_PORT=15037` worked.

## Re-enable one app

Everything is disabled, nothing is uninstalled:

```sh
adb shell pm enable <package>          # e.g. com.tcl.ui_mediaCenter for USB drives
```

## Change the dock order or the wallpaper

Edit `dock_apps` in `config.json` (four package names, left to right) and run `build-and-install.sh` again.
Package names: `adb shell pm list packages -3`.

For a wallpaper, copy this folder's `config.json` into a theme folder, add `"wallpaper": true` and a dark
1920×1080 `wallpaper.jpg`, and push it with `tools/theme.sh <folder>` (see `themes/README.md`).

## Remote buttons

With launcherx disabled, `TclPhoneWindowManager` still turns the app buttons into broadcasts that only
launcherx answered, so they did nothing. Android keycodes on this firmware (read with
`KeyEvent.keyCodeFromString` through `app_process`, not guessed):

| Button | Keycode name | Number | Now does |
|---|---|---|---|
| Netflix | `KEYCODE_TCL_NETFLIX` | 4062 | opens Netflix (`app_keys`) |
| YouTube | `KEYCODE_TCL_YOUTUBE` | 4065 | opens YouTube (`app_keys`) |
| Prime Video | `KEYCODE_TCL_PRIMEVIDEO` | 4099 | opens Prime Video (`app_keys`) |
| TCL | `KEYCODE_TCL_QUICK_MENU` | 4020 | opens Settings (`settings_key_keycodes`) |
| Settings (gear) | `KEYCODE_TCL_SET` | 4077 | opens Settings (`settings_key_keycodes`); TCL logs it as "tcl set short press" |
| Guard | `KEYCODE_TCL_GUARD` | 4140 | nothing (opened TGuard, disabled) |
| Media | `KEYCODE_TCL_USB_MEDIA` | 4057 | nothing (opened the USB player, disabled) |

Input, Home, D-pad, volume and channel keys work without help. The Linux scancodes behind them, from
`getevent -lt`: TCL 227, gear 233, Netflix 242, YouTube 245, Guard 748, Media 236, Prime 752; `Generic.kl`
maps them to the names above. Do not guess from the button's label: the TCL button is `TCL_QUICK_MENU`,
not `TCL_DASHBOARD` (4137). All of the above were checked with the real
remote. Injected keys never reach the accessibility services, so `input keyevent` is not a test.

## Quirks on this firmware

- **Accessibility services after a reinstall**: Android unbinds them on reinstall and binds them again only
  when `enabled_accessibility_services` changes. Writing the same value back leaves them enabled but unbound
  and the remote buttons dead, so `build-and-install.sh` clears the list first. Do not `am force-stop` the
  launcher: the services land in "Crashed services" until the next reinstall.
- `TclAppBoot` refuses to bind services of packages without TCL's `OP_AUTO_START` while their process is not
  running (`callee_does't_have_OP_AUTO_START_permission` in logcat). After a cold reboot the launcher's
  services still bound, because the launcher is Home and its process starts first.
- **Location is switched off on every boot** (`location_mode` back to 0), so the network pill shows "Wi-Fi"
  instead of the network name even with `ACCESS_FINE_LOCATION` granted.
- A second, non-running user (`10: new_user`) exists; the list only touches user 0.
- Live TV and HDMI screenshots are 0 bytes; HDMI rows open `com.tcl.tv/.TVActivity`.
- Saved Wi-Fi networks belonged to `android.uid.system` / `com.android.tv.settings`, so disabling setupwraith
  kept the TV on Wi-Fi.

## Verified

After every batch: Home, the Input key (Quick panel, `com.tcl.suspension`), Live TV (`com.tcl.tv`) and
YouTube playback. After the handover: D-pad (one row per press, Up from the dock to the top bar, top bar
ends stay put), Inputs dropdown (HDMI 1, HDMI 2, AV, Live TV), Home on the real remote, a cold reboot.
Not tested: casting, Miracast, firmware update, several-day stability.

## When you are done

Network ADB on port 5555 is plaintext. On the TV: Developer options → USB debugging off, and
"Revoke USB debugging authorisations"; reboot, then check `nc -vz <tv ip> 5555` fails.
