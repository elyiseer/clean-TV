# Nokia Streaming Box 8000

Sagemcom/NES build, product `SEI600NK4`, device `HND`, Amlogic, 2 GB RAM, Android TV 14
(`UKG3.240613.001`), German UI. Tested on a real unit on 2026-10-20.

| File | What it does |
|---|---|
| `debloat.txt` | The packages that get neutralised, grouped and commented |
| `apply.sh` | Suspends and background-restricts every package in `debloat.txt` (or a batch file you pass) |
| `build-and-install.sh` | Builds the launcher, installs it, pushes `theme/`, turns on the Home redirect and the Clock screensaver |
| `restore.sh` | Undoes all of it: unsuspends, resets app-ops, Home back to stock, backdrop screensaver, default DNS |
| `theme/config.json` | Dock order, hidden apps, Home redirect and the hidden Wi-Fi pill for this box |

Network debugging switches itself off on every reboot: after a restart, turn it on again in
Developer options before connecting. Set `TV_HOST` to the box's IP for every command. The box moves around on DHCP; it advertises
itself over mDNS, so `avahi-browse -rt _adb._tcp` (Linux) or `dns-sd -B _adb._tcp` (macOS) finds it.

```bash
export TV_HOST=192.168.1.50
examples/nokia-streaming-box-8000/build-and-install.sh   # launcher first, so the screensaver has somewhere to go
examples/nokia-streaming-box-8000/apply.sh               # then the debloat
examples/nokia-streaming-box-8000/restore.sh             # undo everything
```

## Why this box needs its own scripts

The firmware answers every `pm disable-user`, `pm disable` and `pm disable-until-used` with
`Warning! This command is illegal!`, refuses `pm hide` (no `MANAGE_USERS` for the shell) and refuses
`pm uninstall --user 0` for its "core" apps. TV Settings has no Disable button either. So
`tools/disable.sh` cannot work here. What still works, and what `apply.sh` does per package:

- `pm suspend --user 0 <pkg>`: the app cannot be opened and its notifications are hidden
- `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`: no background services or jobs
- `am force-stop <pkg>`

All three survive a reboot (checked). It is weaker than disabling: a boot receiver can still wake
briefly. The ad-blocking DNS below catches what such a wake-up would try to send.

The stock home `com.google.android.tvlauncher` declares HOME with priority 2, which no sideloaded
launcher can match, and it cannot be disabled or uninstalled. So `set-home-activity` says `Success`
but changes nothing. Instead the launcher's `HomeKeyService` (an accessibility service) runs with
`"home_redirect_package": "com.google.android.tvlauncher"`: the remote's Home button opens the new
launcher, and whenever the stock home comes up anyway (after boot, or Back out of an app) the new
launcher steps in front of it. After a cold boot the stock home shows for about 20 seconds until
Android binds the service.

## Re-enable an app

Everything: `examples/nokia-streaming-box-8000/restore.sh`.

One app, for example YouTube Music:

```bash
tools/tv.py 'pm unsuspend --user 0 com.google.android.youtube.tvmusic'
tools/tv.py 'cmd appops reset --user 0 com.google.android.youtube.tvmusic'
```

Then comment the line out in `debloat.txt` so a later `apply.sh` leaves it alone. If it is hidden
from the launcher grid (YouTube Music is), remove it from `hidden_apps` in `theme/config.json` and
push the theme (below). Google Assistant is `com.google.android.katniss`; the remote's mic button
works again once it is unsuspended.

## Change the dock order

Edit `dock_apps` in `theme/config.json`. It holds four package names, left to right; find names with
`tools/tv.py 'pm list packages'`. Then push it, which restarts Home but needs no rebuild:

```bash
tools/theme.sh examples/nokia-streaming-box-8000/theme
```

This theme's dock: Netflix, YouTube, Prime Video, Play Store, the apps that ship on the box. Put your own
four there.

## Settings on the TV

Hold OK on the **Einstellungen** pill (top right) and pick **Launcher-Einstellungen**: apps per row,
animations and *Nachfedern* (the bounce of the highlight). A short press on the pill still opens the
TV's own settings.

## Wallpaper

Put a dark 1920×1080 JPEG under about 300 KB at `theme/wallpaper.jpg`, set `"wallpaper": true` in
`theme/config.json` and push the theme as above. Set it back to `false` for plain black. Other keys
(columns, colours, the time-of-day sky with `"sky_intensity": 60`) are in `themes/README.md`.

## Motion

`"focus_overshoot": false` in `theme/config.json` (or *Nachfedern: Aus* on the TV) makes the glass
highlight glide straight onto its tile and the tile zoom in without a bounce, while scrolling stays
smooth. `"reduced_motion": true` is the stronger option for slow GPUs: it also scrolls in one step and
shortens fades.

## Wi-Fi pill, Inputs pill

The Wi-Fi pill is hidden (`"show_network_pill": false`). With Location off, Android 14 only lets apps
show "WLAN", not the network name. Set it to `true` and push the theme to bring it back. There is no
Inputs pill: the box has no HDMI-in or AV, and Nokia's Live TV app ships disabled. Live TV comes
from streaming apps.

## Ad-blocking DNS

Private DNS is set to AdGuard (`dns.adguard-dns.com`): ad and tracking domains such as
`doubleclick.net` resolve to 127.0.0.1 for every app on the box. It does not block YouTube ads (they
come from the same servers as the videos). YouTube was checked to still play with it on. Undo:

```bash
tools/tv.py 'settings put global private_dns_mode opportunistic'
```

## Home redirect off, without the full restore

```bash
tools/tv.py 'settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0'
```

The stock home is back on the next Home press. Turn it on again by re-running `build-and-install.sh`.

## Before you start again

ADB over the network was switched off at the end of the first run. To work on the box again: TV
Settings > Geräteeinstellungen > Info > press OK on Build seven times, then Entwickleroptionen >
USB-Debugging on, and accept the prompt on the TV. Switch it off again when you are done; port 5555
is plaintext.
