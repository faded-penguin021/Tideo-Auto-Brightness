# Debug APK — temporary (delete on merge)

`tideo-auto-brightness-S12.9c-debug.apk` — debug build of branch
`claude/great-fermi-rw07ys` (S12.9c + the context-rule fix below).

> This folder is a throwaway hand-off artifact. **It will be deleted at merge.**
> Debug-signed, not for release. `minSdk 31`, `versionCode 1`.

Install: `adb install -r tideo-auto-brightness-S12.9c-debug.apk`
(or copy to the device and tap it; allow "install unknown apps").

## Context-rule fix (D-074) — please re-test this

Your finding: a per-app rule (e.g. "load Outdoors when Google Photos opens")
did nothing, and no "Context Automation" debug flash appeared. Cause: the engine
only started the foreground-app watcher at service start / screen-on, so a rule
**created while the service was already running** never started watching — it
stayed dormant until a screen off/on or reboot. Fixed: the engine now reacts to
rules being added/edited/deleted and starts the watcher immediately.

To verify:
1. Make sure the service is running, then create the app rule.
2. **Grant usage access** if asked (per-app rules can't read the foreground app
   without it). The Contexts list now shows a red "Per-app rules need usage
   access…" card with a Grant button whenever an app rule exists without the
   grant — if you don't see that card, usage access is already granted.
3. Set debug to **Context Automation** (Live Debug), open Google Photos → you
   should now get the flash and the profile should switch. Switching away should
   revert. (Previously this only worked after toggling the screen off/on.)

Note on the "didn't request usage stats" observation: that's expected if you'd
already granted usage access during onboarding — the app won't ask again. The
real bug was the dormant watcher, not the permission.

## What S12.9c changed (what to look out for)

S12.9c is an engineering-quality / schema pass — **no intended change to the
core auto-brightness behaviour.** Most of it is internal (nested settings model,
fail-fast contract, DataStore docs/tests) and not directly visible. The few
user-facing things to sanity-check on device:

1. **Circadian "Spread" range (Super Dimming screen).** The spread field is now
   signed **−100..100** (was wrongly clamped to 1..300). Entering a value
   outside −100..100 shows a red helper message and the value is clamped on
   Apply. Negative values are now actually honoured — paired with S12.9b this
   means a negative spread *boosts* super-dimming during daylight (needs the
   ELEVATED / WRITE_SECURE_SETTINGS grant to see the dimming effect).

2. **Profile import errors (Profiles & Import/Export).** Importing a file that
   is **not** a Tideo export or a Tasker AAB config now shows a dismissible red
   **error card** ("Couldn't read this profile…") instead of failing silently.
   Valid Tideo exports and legacy Tasker configs still load normally; a
   blank/empty config still loads to defaults.

3. **Validation messages.** A few new advisory (non-blocking) warnings may
   appear in red helper text on the curve / super-dimming / animation fields:
   dimming strength > 65, dimming threshold below min brightness, taper midpoint
   above max brightness, min-wait above max-wait. No warning-triangle glyphs
   anywhere (intentional).

4. **Placeholders.** The unfinished "About & Guide" screen and the deferred
   chart slots now read "not available yet" (no "Coming in Sxx" text). Expected.

## Quick regression sweep (should be unchanged from S12.9b)

- Grant *Modify system settings* → toggle on → brightness tracks the sensor.
- Profiles: apply / save-current-as / overwrite / delete / restore factory.
- Context rules still load their target profile; Resume banner after a manual
  profile load still works.
- Settings persist across an app restart (the on-disk format is unchanged).
