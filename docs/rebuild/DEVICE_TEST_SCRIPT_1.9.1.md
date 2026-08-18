# DEVICE_TEST_SCRIPT_1.9.1 — round script for v1.9.1 (DB-065…DB-070)

**Ephemeral (D-155/DB-010).** Covers only what v1.9.1 changed on top of the released v1.9.0 — not
the full app. Step numbers match the standing `DEVICE_TEST_SCRIPT.md` so results fold back cleanly
(append/extend, never renumber); **delete this file once v1.9.1 ships.** Install the debug APK
(`applicationIdSuffix=".debug"`, coexists with the release install per D-106 — run only one
variant's service at a time, D-128). The debug build needs its **own** ELEVATED grant:

```
adb shell pm grant com.tideo.autobrightness.debug android.permission.WRITE_SECURE_SETTINGS
```

Legend: **[ELEVATED]** needs WRITE_SECURE_SETTINGS. Everything here is §11 (Privileged Display),
except §11e which is Contexts.

## §11a — Stay awake writes the dock bit too (DB-065) [ELEVATED]

The v1.9.0 defect you reproduced: Developer Options writes mask **15**, Tideo wrote **7**, so
turning the toggle off and on again dropped dock stay-awake. **Order matters** — DB-068 makes Apply
skip a write the device does not need, so this only proves anything from an OFF start.

> **Why the adb writes here are safe** (DB-071's test, applied): every value this section writes —
> `0`, `1`, `15` — is one AOSP's own Stay-awake control writes, into a **power** key, not a display
> transform. The worst outcome is a screen that stays on while charging, undone by writing `0`. If
> a step ever asks you to write a value no AOSP code path produces, that step is wrong — skip it
> and say so.

1. Put the device in the pre-state and confirm it:
   ```
   adb shell settings put global stay_on_while_plugged_in 0
   adb shell settings get global stay_on_while_plugged_in     # → 0
   ```
2. Privileged Display → **Stay awake while charging** ON → **Apply** (service OFF).
   ```
   adb shell settings get global stay_on_while_plugged_in     # → 15   ← the fix
   ```
   **Expected: 15**, not 7. On v1.9.0 this step yields 7 — that is the before/after.
3. Toggle it OFF → Apply. **Expected:** `0`.
4. Toggle ON again → Apply. **Expected:** `15` again (the v1.9.0 bug returned 7 here).
5. **Preservation check (the other half).** Set a partial mask by hand, then Apply with the toggle
   already ON and some *other* field changed (e.g. flip Color inversion):
   ```
   adb shell settings put global stay_on_while_plugged_in 1
   ```
   **Expected:** the mask stays **1**. Tideo now leaves a non-zero mask alone rather than
   broadening it — an unrelated Apply must not rewrite a charger set you chose. Turning the toggle
   off and on again is how you deliberately re-assert 15.
6. Sanity, on the phone rather than adb: with stay-awake ON and a short screen timeout, plug in a
   charger — the screen must stay on; unplug — it must time out normally.

## §11b — Unrecognised colour-correction mode (DB-066/DB-069) — READ-ONLY, do not synthesise

> **Do NOT write a synthetic value into `accessibility_display_daltonizer` (or any display key) to
> reach this state.** An earlier draft of this script did; that is a device-damage class this
> project has already been bitten by twice (DB-071) — a Samsung black-screened on an unsupported
> key write, and a hand-written `reduce_bright_colors_level` left a OnePlus recoverable only by
> blind-tapping through the lock screen after a reboot. **The preservation behaviour is verified by
> unit tests only and is an accepted unverified residual on device.**

7. **Read-only observation, no writes.** On any device you already use:
   ```
   adb shell settings get secure accessibility_display_daltonizer
   adb shell settings get secure accessibility_display_daltonizer_enabled
   ```
   If the value is one of `-1, 0, 11, 12, 13` (or the key is unset), this step is **SKIPPED —
   nothing to observe**, which is the expected outcome on AOSP-faithful hardware.
8. **Only if a device reports something else on its own** — an OEM correction mode you did not
   write — then, and only then: open Privileged Display and confirm the note *"Android is using a
   correction mode this app does not recognize…"* appears under the chips, and that changing an
   unrelated field and applying leaves both keys as they were. Report the raw values before and
   after. Do not create this state deliberately.
9. **The chips themselves still need their ordinary check** — that part is safe, because every value
   the app writes is one AOSP defines: pick Grayscale, then each correction mode, confirming the
   screen changes and system Settings agrees (standing script §11 step 32 covers this in full).

## §11c — Apply writes only what differs (DB-068) [ELEVATED]

11. Service OFF. Set the device by hand to exactly what the screen shows (e.g. inversion on in both
    places), then change **one** other field and Apply. **Expected:** the untouched fields do not
    flicker or re-assert; nothing you set outside the app is overwritten. Practical check: leave
    Night Light ON in system Settings with the app's Night Light also ON, change only stay-awake,
    Apply — Night Light must not blink off/on.
12. ~~**The failure banner still works.** Revoke the grant, change a field, Apply; expect the
    write-failed banner.~~ **WITHDRAWN 2026-08-18 — not reachable, and the reason is the design
    working (DB-072).** Revoking mid-round and returning to the app makes `refresh()` re-probe the
    tier on ON_RESUME, so the screen replaces the toggles with the grant card (D-149's self-guard):
    there is no Apply left to press. The run that found this is the evidence — grant card, correct
    debug package name, no crash. What the step meant to check survives only as a RACE (grant lost
    between opening the screen and tapping Apply), which cannot be staged by hand; it is covered by
    `applyNowBelowElevated_surfacesFailure_andWritesNothing`. **Instead, confirm the recovery
    path:** re-grant, reopen the screen, and the toggles come back with the device's values.

## §11d — Panic still resets everything (D-155 regression check) [ELEVATED]

13. With several toggles on (including a preserved/unrecognised correction mode if you set one up),
    fire **Emergency Reset**. **Expected:** unchanged from v1.9.0 — colour correction off, inversion
    off, supported Night Light/AOD off, stay-awake `0`. Panic writes unconditionally; the
    preservation rules above deliberately do **not** apply to it.

## §11e — Location listener release (DB-067) — best-effort only

14. There is no user-visible symptom and no way to force the code path from the UI (it needs a
    permission loss between two instructions inside one call). Treat as a **soak observation**: use
    "Use current location" in a Contexts rule a few times, revoke and re-grant Location permission
    between attempts, and watch for the GPS status-bar indicator staying on after the app settles.
    **Expected:** no indicator left on, no battery-blame entry for Tideo afterwards. Report
    "not observed" rather than PASS — this one is proven only by code inspection.

## Reporting

Per step: PASS / FAIL / BLOCKED / SKIPPED, plus the raw `adb` output for §11a and §11b — those two
are the ones where a number, not an impression, is the evidence. FAILs go to the Owner queue in
`docs/STATE.md` with the step number.
