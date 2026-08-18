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

## §11b — An unrecognised colour-correction mode is preserved (DB-066/DB-069) [ELEVATED]

Only reachable on a device whose OEM ships a correction mode outside AOSP's set
(`-1, 0, 11, 12, 13`). If `adb shell settings get secure accessibility_display_daltonizer` never
shows anything else, **simulate it** — that is a legitimate run of this step, not a shortcut:

```
adb shell settings put secure accessibility_display_daltonizer 42
adb shell settings put secure accessibility_display_daltonizer_enabled 1
```

7. Open Privileged Display. **Expected:** under the colour-correction chips, the note *"Android is
   using a correction mode this app does not recognize…"*. No chip claims to be the active mode.
8. Change something unrelated (Color inversion, or Night Light) → **Apply**.
   ```
   adb shell settings get secure accessibility_display_daltonizer          # → 42, unchanged
   adb shell settings get secure accessibility_display_daltonizer_enabled  # → 1, unchanged
   ```
   **Expected:** both unchanged. On v1.9.0 this silently switched the mode off.
9. Now pick **Grayscale** in the app → **Apply**. **Expected:** value `0`, enabled `1` — an explicit
   pick still wins; only the *unasked* write is suppressed. The notice disappears on the next
   screen open.
10. **Regression guard for the fix's own bug (DB-069).** Restore the recognised "off" value:
    ```
    adb shell settings put secure accessibility_display_daltonizer -1
    adb shell settings put secure accessibility_display_daltonizer_enabled 1
    ```
    Reopen the screen. **Expected:** the **Off** chip is selected and there is **no** preservation
    notice — `-1` is a value the app knows, not a custom mode. (An intermediate build got this
    wrong and showed the notice.)

## §11c — Apply writes only what differs (DB-068) [ELEVATED]

11. Service OFF. Set the device by hand to exactly what the screen shows (e.g. inversion on in both
    places), then change **one** other field and Apply. **Expected:** the untouched fields do not
    flicker or re-assert; nothing you set outside the app is overwritten. Practical check: leave
    Night Light ON in system Settings with the app's Night Light also ON, change only stay-awake,
    Apply — Night Light must not blink off/on.
12. **The failure banner still works.** Revoke the grant
    (`adb shell pm revoke com.tideo.autobrightness.debug android.permission.WRITE_SECURE_SETTINGS`),
    change a field, Apply. **Expected:** the write-failed banner appears — the diff-write must not
    swallow a permission failure. Re-grant afterwards.

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
