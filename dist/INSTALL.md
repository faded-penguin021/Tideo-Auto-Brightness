# Tideo Auto Brightness — debug build (0.9.0) for Gate-3 testing

`tideo-0.9.0-debug.apk` — the S14 acceptance-candidate debug build. **Temporary:** delete this `dist/`
folder once Gate 3 is signed off and the PR is merged.

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

## Recovery

Stuck on a black/too-dark screen? **Flip the phone upside-down and shake** — Tideo answers with an SOS
vibration and forces brightness to maximum. (Tip: it's deliberately suppressed for ~3 s right after you
wake the screen, so a pocket-grab won't trigger it.)

## Notes

- Debug build: unsigned-for-release, larger than the eventual release APK, and not optimized — fine for
  acceptance testing, not for distribution.
- If the service stops adapting after a while, exempt Tideo from battery optimization (see
  dontkillmyapp.com).
