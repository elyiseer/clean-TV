# Optional CPU access through Shizuku

CPU access through Shizuku is off by default and independent of theme/layout preferences. It does not enable show_stats automatically. Use Launcher settings to opt in and authorize access; setup is explicit. All Shizuku controls and help text are hidden when its app is not installed. Availability is checked whenever Launcher settings resumes; installing Shizuku makes the options appear on return.

Install Shizuku yourself and follow https://shizuku.rikka.app/guide/setup/. Use the startup command shown inside Shizuku’s View command dialog; do not assume an old start.sh path exists. On unrooted devices Shizuku must be started again after a reboot. Nothing installs the app or enables debugging automatically.

The integration uses API/provider 13.1.5 and a non-daemon UserService exposing only aggregate /proc/stat counters. It accepts no commands or arbitrary file paths. Guest time is not double counted, steal is counted, and malformed/reset counter deltas are rejected.

CPU sampling follows the existing ten-second Home stats worker. Leaving Home removes the user service. Missing permission, missing services, startup sampling or unreadable counters leave CPU hidden while RAM/storage remain visible; a valid percentage appears on a later sample.

Unit tests cover counter arithmetic, parsing, permission/lifecycle gating and source loss. An authorized Android TV test with official Shizuku 13.6.0 confirmed real percentages, stopping the CPU process when another app was foreground, and hiding/restoring CPU after a server stop/restart. Reboot and on-device permission-revocation checks are not claimed. Device-identifying records remain private.
