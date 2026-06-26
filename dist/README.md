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

## Fixes bundled in this build (beyond the bare targetSdk bump)

- **Curve wizard now auto-copies %AAB_Test to the clipboard** on every successful run (Tasker
  task38 act13 parity), and the action buttons now wrap so **"Copy full report" is reachable**
  (it was pushed off-screen before). (D-102)
- **Super-dimming / PWM threshold range raised 0..100 → 0..255** so dimming can engage above
  brightness 100, matching the uncapped Tasker field. (D-101)

## Findings investigated and NOT changed (with why)

- **Did not start on reboot** — not the SDK bump. `specialUse` FGS is allowed from BOOT_COMPLETED
  on both Android 15 and 16; the boot code is correct. Most likely Samsung's auto-start/battery
  restriction on a freshly sideloaded package (your stable app earned that standing over time).
  Check: Settings → Apps → "Tideo AB (Debug)" → Battery → **Allow background activity** /
  unrestricted, and any Samsung "Auto-launch" toggle. Also confirm the service was enabled in the
  debug app before rebooting.
- **Circadian "noon @ 12"** — not a bug. The graph x-axis is UTC and falls back to longitude 0
  (solar noon ≈ 12:00 UTC) when it has no location fix. Grant Location to the debug app (or pin a
  fixed lat/lon on the Circadian screen) and the noon marker moves to true solar noon.
- **Circadian scale uses defaults on screen-on (1.14 vs 1.15)** — confirmed real parity gap
  (D-103, OPEN): the once-a-day location isn't persisted across process restarts, so a cold start
  uses TimeContext defaults until re-acquired. Recommended fix tracked for a focused follow-up.
- **Override scaling (unscale + clamp 255)** — verified already ported (OverrideRules, task561).
- **Export location** — exported profiles go wherever you pick in the system "Create document"
  dialog (e.g. Downloads), as a `.json`; Import reads it back. That's the round-trip.
- **Dawn/sunrise & dusk/sunset label overlap** — deferred (D-104): the fix lives in the
  fenced chart engine; minor cosmetic, not worth breaching the fence here.

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
