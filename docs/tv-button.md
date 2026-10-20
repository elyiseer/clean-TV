# TV button

Hold OK on Settings, open Launcher settings, then TV button. Choose Follow configuration, Input switcher, Live TV, or an installed app. The button choice is independent of Live TV in Inputs and survives theme/layout resets.

Enable the existing Input button or Home button accessibility helper through the status row when necessary. No new accessibility service is added. Follow configuration preserves app_keys and each helper’s defaults. Other choices override only KEYCODE_TV.

Destinations are cached and refreshed when preferences or installed packages change. A complete uncancelled press launches once; unavailable destinations show a message. The app chooser displays app names only, retaining package identifiers internally. Duplicate app names remain separately selectable with deterministic internal ordering.

Validated with a physical Android TV remote from Home and another app using InputKeyService. Injected ADB events alone do not reach accessibility key filtering. Device-specific configuration and validation records remain private.
