# Tideo Auto Brightness — debug build (0.9.0) for Gate-3 testing

`tideo-0.9.0-debug.apk` — the S14 acceptance-candidate debug build, **refreshed with the Gate-3
punch-list fixes (G3-F1…F18)**. **Temporary:** delete this `dist/` folder once Gate 3 is signed off
and the PR is merged.

## Install

```bash
adb install -r dist/tideo-0.9.0-debug.apk
```

…or copy the APK to the phone and tap it (allow "install unknown apps" for your file manager).
`minSdk` 31 (Android 12+). It installs alongside nothing else — package `com.tideo.autobrightness`.

## First run (5 steps)

1. **Disable the system's stock Adaptive/Auto Brightness** (Settings → Display) — otherwise the OS and
   Tideo fight over the slider.
2. Open Tideo; complete onboarding: grant **Notifications** and **Modify system settings**
   (WRITE_SETTINGS). The tier badge should read **BASIC**.
3. On the **Dashboard**, flip the **master switch** on. A persistent notification appears.
4. Cover / uncover the light sensor → brightness should animate smoothly down/up; the big number rolls
   and the bar fills/depletes.
5. **(optional, for super dimming)** grant elevated access once:
   ```bash
   adb shell pm grant com.tideo.autobrightness android.permission.WRITE_SECURE_SETTINGS
   ```
   …or use the Shizuku/root path in onboarding. The badge flips to **ELEVATED** on the next screen-on.

## What to test

Run the full **`docs/rebuild/DEVICE_TEST_SCRIPT.md`** end-to-end (it's the Gate-3 script: core loop,
override/resume, screen off/on, proximity damp, panic gesture, super dimming, circadian, contexts,
charts/wizard, **power-draw calibration**, QS tile + widget, 24 h soak). Log misses in
`docs/rebuild/STATE.md` → "Gate findings".

### First, re-verify the Gate-3 punch-list fixes

These are the specific deviations from the previous Gate-3 pass, now fixed:

- **G3-F3 — Min brightness 0 sticks.** Misc → set **Min brightness = 0** → Apply. The dirty
  Apply/Discard prompt should clear and 0 should persist (re-open Misc; it still reads 0).
- **G3-F4 — QS tile on first add.** Dashboard → **Add Quick Settings tile** on a device that
  doesn't have it yet → the toast says *"tile added"*, **not** "already added".
- **G3-F5 — Dashboard animates.** Cover/uncover the sensor → the big number **rolls** to the new
  value and the bar **eases**, *during* the transition (not a snap after it settles).
- **G3-F6 — no daytime super-dim residual.** ELEVATED + super dimming on + **Spread (Circadian) =
  100**, in bright/daylight → Extra Dim should be fully **off** (no faint residual dim).
- **G3-F11 — Reset to auto works.** Drag the system slider (or trigger an override) → Dashboard →
  **Reset to auto** snaps back to the computed brightness and clears the override pause.
- **G3-F15 / F2 — graphs.** Reactivity → the smoothing-α graph x-axis reads **"Relative lux change
  %"** (not "Lux change"); brightness + α graphs show a live **"Now"** marker while running.
- **G3-F16 / F17 — curve wizard.** Tools → the wizard has an **Inertia (τ)** slider (default
  ~0.001); after **Apply suggestion**, the Curve & Brightness graph returns to the **teal** live
  curve (the blue "Suggested" line is gone) and the suggestion tracks your points well.
- **G3-F1 — copy.** Menu → User Guide / About say **"Tideo"** (not "AAB"/"Advanced Auto
  Brightness"), and the guide says **tap the ⓘ** for help (not "long-press").
- **G3-F12 — privacy.** Circadian → Date & location has an **"IP-based location fallback"** toggle
  (off = never contacts ip-api.com).
- **G3-F13 — onboarding.** The restricted-settings card says to **"tap it anyway"** and mentions
  **Usage access**, not just Modify system settings.
- **G3-F14 / F10 — menu.** The **Profiles & Contexts** card is a quieter hero (no teal edge); the
  **Curve & Brightness** row icon is a pencil, no longer a duplicate gear.

## Recovery

Stuck on a black/too-dark screen? **Flip the phone upside-down and shake** — Tideo answers with an SOS
vibration and forces brightness to maximum. (Tip: it's deliberately suppressed for ~3 s right after you
wake the screen, so a pocket-grab won't trigger it.)

## Notes

- Debug build: unsigned-for-release, larger than the eventual release APK, and not optimized — fine for
  acceptance testing, not for distribution.
- If the service stops adapting after a while, exempt Tideo from battery optimization (see
  dontkillmyapp.com).
