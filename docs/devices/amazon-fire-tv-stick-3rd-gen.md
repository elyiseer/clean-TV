# Fire TV Stick 3rd generation: delayed Home redirect

Tested on 20 October 2026, in one session:

- Model AFTSSS, device/product `sheldonp`.
- Fire OS 7.7.1.7, build `PS7717.5741N`.
- Android 9, API 28, current user 0.

This is a compatibility report, not a debloat list or a supported Home takeover.
The experiment used a separately built launcher (`tv.cleanmy.home`), not this
repository's reference launcher or its `HomeKeyService`. The opt-in port is
documented in [FIRE_TV_HOME.md](../FIRE_TV_HOME.md); the source experiment and
the port are distinguished rather than claiming identical device coverage.

## Standard Home replacement did not work

The custom APK installed and declared the HOME category. Setting its Home
activity with `cmd package set-home-activity` printed `Success`, but the Home
resolver and Home key still selected
`com.amazon.tv.launcher/.ui.HomeActivity_vNext`.
Disabling `com.amazon.tv.launcher` with `pm disable-user --user 0` was refused
as a protected package. The stock launcher stayed enabled.

## An accessibility redirect had a five-second delay

An experimental accessibility service watched `TYPE_WINDOW_STATE_CHANGED`
activity names for Amazon Home, then requested the custom launcher. It did not
retrieve window contents or filter raw keys. The service was enabled and bound
for user 0, including after a reboot.

Being bound did not make Home replacement immediate. Timed activity samples
and device logs showed this sequence on the tested build:

```text
16:55:02.090  Amazon Home requested
16:55:02.286  Accessibility event names Amazon Home
16:55:02.621  Custom launcher start requested
16:55:02.625  ActivityManager: Activity start request from <launcher UID> stopped
16:55:07.254  Custom launcher window becomes active
```

The UID is redacted; the times are from the same device log. Foreground activity
sampling showed Amazon Home until approximately five seconds after Home, then
the custom launcher. Both direct `startActivity` and an immutable activity
`PendingIntent` showed the delay. A 200 ms event delay did not remove it.
The evidence points to a firmware app-switch hold, rather than five seconds of
launcher loading. It does not establish that every Home workaround has this limit.

The owner initially reported Amazon Home remaining, later confirmed the custom
four-app dock after waiting ten seconds, and then reported Amazon Home again.
Do not treat one successful redirect as proof of reliable physical Home behavior.
An injected Home key alone is also insufficient; test the real remote from
inside another app, and record both the result and the delay.

The reference `HomeKeyService` filters `KEYCODE_ALL_APPS`, not ordinary
`KEYCODE_HOME`. This experiment does not show that enabling that service gives
Fire OS Home takeover. It also does not test Projectivy, overlay grants, root,
Launcher Manager, or firmware downgrades.

## Check and recover

Read the current user, enabled-services setting, accessibility dump, foreground
activity and a bounded device log. Check the service is bound for that user,
then look for a stopped/deferred activity start before assuming the helper
needs a shorter timer. See [the accessibility checks](../TROUBLESHOOTING.md#accessibility-helper-enabled-but-the-remote-button-does-nothing).

Preserve other components in the colon-separated accessibility-service list.
The experimental helper was removed from that list to verify return to stock:
the service became unbound and Amazon Home resumed. Re-enabling the helper
restored the delayed redirect. Keep an explicit local action that disables
redirection and opens Amazon Home so the owner can return without a computer.

On this build, `settings --user 0 delete secure ...` was rejected with
`--user not supported for delete and list.` A recovery script checked
`am get-current-user` was 0 before using `settings delete secure ...` without
`--user`. Do not assume this implicit-user path is safe on another active user,
and do not clear every accessibility service as a blanket reset.

No claim of multi-day reliability or removal of Fire OS's Home restriction is made.
