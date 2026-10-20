# Philips Google TV TA7 (Brazil)

Philips Google TV TA7 (TPV, PH3M_AL_T32, MediaTek), Google TV 14, build UTT1.250308.001, about 1.9 GB RAM.
Debloated and given the Clean Your TV launcher on 2026-10-20.

| File | What it is |
|---|---|
| `debloat.txt` | The packages that were disabled, in the batches they were applied, with what each one is and what was kept on purpose |
| `install.sh` | Builds the launcher, installs it as Home (disabling the setup wizard and the stock launcher), turns on the gear-button fix and pushes `theme/` |
| `restore.sh` | Undoes all of it: re-enables every package in `debloat.txt`, gives Home back to the stock Google TV launcher and turns AdGuard DNS off |
| `theme/config.json` | Dock order, greeting, hidden apps |

All commands run from the repository root and need the Python client once:
`python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt`.
They also need network ADB, which is switched off when you're done (see the last section), so turn it on
again first: Settings > System > About > press Build 7 times, then Developer options > USB debugging on.
Accept the "Allow USB debugging?" prompt on the TV the first time a command connects.

## What changed

- **Disabled** (never uninstalled): TP Vision analytics (`candeebug`, `gel`), partner attribution and
  auto-installs, European satellite operator apps, the ad-consent and disclaimer popups, Play Games,
  calendar sync, print spooler, Netflix, Prime Video, the Google TV films app, Chromecast, the setup
  wizard and the stock Google TV launcher.
- **Re-enabled**: HBO Max (`com.wbd.stream`) and Google Assistant (`com.google.android.katniss`), which
  had been disabled before.
- **Kept**: everything that plays HDMI and the tuner, the input menu, remote pairing, keyboards, Play
  Store and Play Services, firmware updates, and all the streaming apps you use.
- **Launcher**: `com.gathin.tvhome` is Home. Dock: YouTube, HBO Max, Crunchyroll, CineVS.
- **Gear button**: the remote's Settings button only worked through the stock launcher, so the
  launcher's HomeKeyService (an accessibility service) now handles it. If the button stops working,
  check Settings > Accessibility > "Botão Home" is on, or run `install.sh` again.
- **Private DNS**: `dns.adguard-dns.com`, which blocks ad and tracker domains for the whole TV. It does
  **not** block YouTube ads.

## Re-enable an app

On the TV: Settings > Apps > See all apps > Show system apps (or "Disabled apps"), pick the app, Enable.

Over ADB:

```bash
TV_HOST=192.168.0.10 tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.netflix.ninja'
```

Then delete or comment out its line in `debloat.txt`, so a later run doesn't disable it again. The
package names and what each one does are in `debloat.txt`.

## Change the dock order

Edit `theme/config.json`. `dock_apps` lists the four dock apps from left to right:

```json
"dock_apps": ["com.google.android.youtube.tv", "com.wbd.stream", "com.crunchyroll.crunchyroid", "com.cnvs.apptv"]
```

Other package names: Globoplay `com.globo.globotv`, Disney+ `com.disney.disneyplus`, Spotify
`com.spotify.tv.android`, YouTube Music `com.google.android.youtube.tvmusic`, Red Bull TV
`com.nousguide.android.rbtv` (all of them: `tools/tv.py 'pm list packages'`). Then push it; no rebuild:

```bash
TV_HOST=192.168.0.10 tools/theme.sh examples/philips-google-tv-ta7/theme
```

You can also reorder on the TV itself: hold OK on the Settings pill and choose the reorder option.
`greeting_name` sets the name after the greeting; `hidden_apps` hides tiles.

## Change the wallpaper

Put a dark 1920×1080 JPEG under about 300 KB at `theme/wallpaper.jpg`, set `"wallpaper": true` in
`theme/config.json`, and push the theme with the command above. For a rotating set, put the pictures in
`theme/wallpapers/` and add `"wallpaper_rotation_minutes": 30` (and `"wallpaper_dim": 40` to darken them).
`"background_color"` changes the plain black. To go back to plain black, set `"wallpaper": false` and push
again. All keys are in [themes/README.md](../../themes/README.md).

## Turn AdGuard DNS off

```bash
TV_HOST=192.168.0.10 tools/venv/bin/python -I tools/tv.py 'settings put global private_dns_mode opportunistic'
```

Or Settings > Network & Internet > Private DNS > Automatic. Do this first if an app stops loading.

## Undo everything

```bash
TV_HOST=192.168.0.10 examples/philips-google-tv-ta7/restore.sh                       # keep the launcher installed
UNINSTALL_LAUNCHER=1 TV_HOST=192.168.0.10 examples/philips-google-tv-ta7/restore.sh  # remove it too
```

`tools/restore.sh examples/philips-google-tv-ta7/debloat.txt com.google.android.apps.tv.launcherx` does
the same from the state recorded before the first change (`~/.cleanyourtv/`). Either way, the apps that
were already disabled before 2026-10-20 (Aptoide, VS Cine, Viki, the ambient screensaver and the
others listed at the top of `debloat.txt`) stay disabled. Re-enable them one by one as above.

## Reinstall or update the launcher

```bash
TV_HOST=192.168.0.10 examples/philips-google-tv-ta7/install.sh
```

Needs JDK 17+ and the Android SDK (platform 34, build-tools 34) in `~/Android/Sdk`. A reinstall switches
accessibility services off, and the script turns the gear-button service back on.

## Close ADB when you're done

Port 5555 is plaintext, so leave it off: Settings > System > Developer options > switch off USB debugging
(and Wireless debugging if it is on), choose "Revoke USB debugging authorisations", then restart the TV.
From the PC, `nc -vz 192.168.0.10 5555` should then fail.
