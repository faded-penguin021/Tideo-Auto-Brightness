# DEVICE_TEST_SCRIPT_1.9.0 — round script for the v1.9.0 train (DB-034…DB-046)

**Ephemeral (D-155/DB-010).** Covers only what THIS unreleased train changed — not the full app.
Numbered to match the corresponding step in the standing `DEVICE_TEST_SCRIPT.md` so results fold
back cleanly (append/extend, never renumber) and this file is **deleted** once v1.9.0 ships. Build
+ install the debug APK (`applicationIdSuffix=".debug"`, coexists with a release install per
D-106 — run only one variant's service at a time, D-128).

Legend: **[ELEVATED]** needs WRITE_SECURE_SETTINGS.

## §11.32 — Night Light / AOD hide + fail closed when unsupported (DB-041…DB-043)

On a device (or OEM build) where the framework reports the feature unavailable:

1. Open **Menu → Privileged → Privileged Display**. **Expected:** the **Night Light** row is
   hidden entirely if `config_nightDisplayAvailable` is false; the **Always-on display** row is
   hidden if `config_nightDisplayAvailable` is false OR `config_dozeComponent` is empty (AOD
   requires both — DB-043's conjunction, not just the headline flag).
2. With a profile/context rule that *would* carry Night Light or AOD, trigger a profile swap,
   a circadian tick, direct Apply, and panic in turn. **Expected:** every path is a harmless
   no-op on the unsupported field — no write, no crash, no stale UI claiming an unsupported
   value is set.
3. On a device where the feature IS supported, confirm the row is shown and still behaves as
   before (see §11.32 in the standing script for the full write+read-back check) — this train
   must not regress the supported case while fixing the unsupported one.

## §11.32 — Disable HDR (experimental, Android 14+) (DB-044/DB-045)

4. **Menu → Privileged → Privileged Display → Disable HDR (experimental)** on. **Expected:**
   read back `user_disabled_hdr_formats=1,2,3,4` and
   `are_user_disabled_hdr_formats_allowed=0`. Off: `allowed` returns `1`, formats clears.
   **Expected:** this is a stored-preference write, not Force-SDR — either direction may require
   a reboot, and an HDR/display-mode transition may briefly blank the screen. Confirm both
   caveats are visible to you as a real (if brief) UX event, not just documented.
5. Externally set a **partial/malformed** `user_disabled_hdr_formats` row (e.g. `adb shell
   settings put secure user_disabled_hdr_formats 2,3` with the allowed flag `1`), then open
   Privileged Display. **Expected:** the switch is replaced by a custom-preference preservation
   notice (not a Boolean guess) — and applying an unrelated field on this screen leaves that row
   untouched rather than broadening it to the full disabled set.

## §11.39a — Read-back tracks the device, not the stored profile (DB-034/DB-039)

6. With Privileged Display open, flip Night Light (or color inversion) from the system
   quick-settings tile, return to Tideo. **Expected:** the toggle shows the device's actual
   state and Apply becomes available.
7. Flip the SAME toggle back from the tile and return again. **Expected:** the screen follows a
   **second** time — before DB-039 this worked exactly once per screen entry.
8. Change a toggle WITHOUT applying, background the app, return. **Expected:** your uncommitted
   edit survives — read-back never overwrites a dirty draft.

## §13.44a — Dropped commands explain themselves (DB-035)

9. Set **Live Debug Info → Log Level** to 8. Send a control broadcast that will be rejected
   (e.g. gate the automation OFF, then broadcast `LOAD_PROFILE` with a caller-chosen name over
   ~40 chars or containing control/bidi characters):
   `adb shell am broadcast -a com.tideo.autobrightness.control.LOAD_PROFILE -n
   com.tideo.autobrightness.debug/com.tideo.autobrightness.app.control.ControlReceiver --es name
   "<long or odd name>"`.
   **Expected:** the debug log names the drop reason at level 8; the profile name reaching the
   system-wide overlay is truncated to 40 chars with control/bidi characters stripped.

## §5.14a — Every panic entry point confirms exactly once (DB-037)

10. Fire panic via the inversion gesture. **Expected:** S.O.S. vibration, max brightness,
    service stop.
11. Repeat via the foreground notification's **Reset** action, then via the intent surface:
    `adb shell am broadcast -a com.tideo.autobrightness.control.PANIC -n
    com.tideo.autobrightness.debug/com.tideo.autobrightness.app.control.ControlReceiver` (automation toggle ON).
    **Expected:** the same S.O.S. vibration both times — before DB-037 only the gesture buzzed.
12. Fire the gesture once more. **Expected:** vibrates **exactly once**, not twice (checks for a
    leftover call site on the shared path).

## Log any miss

Log any FAIL/BLOCKED in `../STATE.md` → Owner queue with the step number above, not the master
script's numbering, until this file is folded in and deleted.
