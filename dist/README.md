# dist/ — TEMPORARY test artifacts (delete before merge to `main`)

Debug APK for on-device testing of the **targetSdk 36 (Android 16)** bump on branch
`claude/sdk-target-version-compat-wvma0e`. Squash-merge keeps this off `main`; delete
`dist/` regardless before merging.

## The APK

`tideo-auto-brightness-1.1.0-targetSdk36-debug.apk`

- `versionName 1.1.0-debug`, `versionCode 7`, `targetSdk 36` / `compileSdk 36`
- **Separate package: `com.tideo.autobrightness.debug`**, label **"Tideo AB (Debug)"** —
  installs *alongside* your stable signed release. Different applicationId ⇒ its own data
  dir, so your release app's profiles and context rules are untouched.
- Debug-signed (not the release key).

Install (won't touch the stable app):

```
adb install -r dist/tideo-auto-brightness-1.1.0-targetSdk36-debug.apk
```

## Heads-up (consequences of the separate package)

- The debug app starts **empty** — it does NOT see the stable app's profiles/context
  rules (separate storage, by design). Re-create a couple of rules to exercise the paths.
- ELEVATED tier: `WRITE_SECURE_SETTINGS` is granted per-package, so grant it to the debug
  package separately: `adb shell pm grant com.tideo.autobrightness.debug android.permission.WRITE_SECURE_SETTINGS`
- Shizuku authority is `com.tideo.autobrightness.debug.shizuku` (isolated; no conflict).
- Both apps may show a QS tile / widget with the same name — that's cosmetic only.

## TODO — on-device acceptance (owner)

Automated ladder is green under SDK 36; the OS-behavior checks below can't be covered by
JVM/Robolectric. Tick as you go.

### Pass A — regression (nothing that worked is broken)
- [ ] Onboarding: every permission step (WRITE_SETTINGS, notifications, usage access, location incl. background, Shizuku)
- [ ] BASIC core loop: brightness tracks lux through profile gates
- [ ] ELEVATED super-dimming below threshold (after pm grant to .debug)
- [ ] Proximity damping (LuxAlpha ×0.1, not pause)
- [ ] Profile gates: charging, low battery, panic, screen-off, per-app, Wi-Fi SSID, location, time-of-day
- [ ] Wi-Fi SSID with Location services OFF (Shizuku/dumpsys path)
- [ ] Per-app context rule (UsageStats foreground-app reader)
- [ ] Manual brightness override detected (ContentObserver)
- [ ] FGS persists 30 min with screen locked
- [ ] Survives reboot (BootCompletedReceiver restarts service)
- [ ] QS tile toggle syncs with app
- [ ] Widget TOGGLE/RESET, event-driven repaint
- [ ] Ongoing notification + both action buttons
- [ ] Accessibility flash overlay on → system-wide; off → foreground toast
- [ ] Curve wizard diagnostics land in clipboard
- [ ] Settings import/export round-trip
- [ ] All 9 screens — no clipped insets (edge-to-edge)
- [ ] Panic S.O.S. vibration
- [ ] Geo-IP fallback (Location off → ip-api.com)
- [ ] 24h soak: no unexpected dimming / service kills / battery anomaly

### Pass B — feature availability (OS still hands us what we need)
- [ ] Light sensor uninterrupted in background under specialUse FGS (logcat ~30 min, screen off)
- [ ] Background location from non-location FGS (circadian sun refresh while backgrounded)
- [ ] pm-grant WRITE_SECURE_SETTINGS survives reboot on Android 16
- [ ] Accessibility service not auto-disabled after 24h
- [ ] Doze/standby buckets don't kill the FGS (covered by soak)

### Cleanup
- [ ] Delete `dist/` before merging to `main`
- [ ] Owner tags `v1.1.0` after merge
