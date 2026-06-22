# Tideo Auto Brightness

[![Build](https://github.com/faded-penguin021/tideo-auto-brightness/actions/workflows/build.yml/badge.svg)](https://github.com/faded-penguin021/tideo-auto-brightness/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-0.9.0-informational)](https://github.com/faded-penguin021/tideo-auto-brightness/releases)
[![minSdk](https://img.shields.io/badge/minSdk-31-success)](https://developer.android.com/about/versions/12)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)

**A glass-box replacement for Android's adaptive brightness.** You taught Android your brightness
preferences and it is *still* wrong. Tideo replaces the opaque on-device ML with deterministic,
inspectable math: a tunable lux→brightness curve you can see and shape, smooth animated transitions,
circadian (time-of-day) scaling, per-context overrides, and an optional privileged "super dimming"
mode that takes the screen below the system minimum.

Tideo is a ground-up Kotlin/Compose rebuild of the **[Advanced Auto Brightness][aab]** (AAB) Tasker
project, ported segment by segment to a native app with exact behavioural parity — the math and
decision logic are golden-tested against a transcription of the original Tasker engine.

> **Tideo is the native-app build of AAB.** The Tasker project [Advanced Auto Brightness][aab] is the
> upstream **source of truth**. Feature discussion, bug triage, and contributions happen there — see
> [Contributing](#contributing).

## Features

- **Three-zone perceptual brightness curve** with a live, editable graph (low-light √, mid-range
  ∛-ish, high-light asymptotic tail — C0-continuous).
- **Automatic curve fitting** — enable override detection, adjust brightness by hand during normal
  use, and after ~9 points across varied lighting the wizard suggests a fitted curve.
- **Reactivity control** — per-range dead zones (dark / dim / bright) that kill sensor jitter without
  making the screen sluggish.
- **Smooth animated transitions** with read-back override detection (drag the system slider and Tideo
  steps aside, then resumes per your rules).
- **Circadian scaling** synced to local sunrise/sunset (GPS, manual lat/lon, or IP-geolocation
  fallback) — shifts the whole curve warmer/dimmer across the day.
- **Super dimming** (privileged) — drives Android's *Reduce Bright Colors* below the hardware floor,
  plus a PWM-flicker-aware software-dimming mode.
- **Context automation** — load profiles automatically by foreground app, time window, location,
  charging state, Wi-Fi SSID, or day of week, with priority-based conflict resolution.
- **Profiles** — five built-in presets (Default, Battery Saver, Video, Outdoors, Night Reading), plus
  save / overwrite / factory-restore and JSON + legacy-Tasker import/export.
- **Live Debug scene** — a glass-box readout of every runtime `%AAB_*` variable and the decisions the
  pipeline is making right now.
- **Emergency recovery** — flip the phone upside-down and shake to force brightness to max with an SOS
  vibration, if a setting ever leaves you in the dark.
- **Surfaces** — foreground-service notification with actions, a Quick Settings tile, a home-screen
  widget, and boot-start.

## How it works

- **Three-zone model.** Brightness is a C0-continuous piecewise function of lux; each zone has its own
  scaling so low light stays sensitive and high light compresses gracefully.
- **Curve fitting.** With override detection on, your manual corrections become training points; the
  wizard fits the three-part function and reports per-zone fit quality.
- **Event-driven runtime.** The pipeline reacts to state changes (a light step past the dead band, an
  app switch, a battery delta, a location drift) rather than polling — easy on the battery.
- **Context precedence.** When several context rules match, highest priority wins; on a tie, the most
  specific rule wins.

## Install

1. **Disable** the system's stock Adaptive/Auto Brightness (Settings → Display).
2. Install the APK — a debug build is published under [Releases][releases] (or, during testing, a
   `dist/app-debug.apk` is provided directly).
3. Launch Tideo, complete onboarding (grant notifications + *Modify system settings*), and toggle the
   **main service** on from the Dashboard.

`minSdk` 31 (Android 12) · `target`/`compile` SDK 35.

## Privilege tiers

Tideo works at two levels; the core pipeline is fully functional at **BASIC**.

| Tier | Permission | Unlocks |
|---|---|---|
| **BASIC** | `WRITE_SETTINGS` (user-grantable, in-app) | Curve, animation, reactivity, circadian, contexts |
| **ELEVATED** | `WRITE_SECURE_SETTINGS` (one-time `pm grant`) | Super dimming (Reduce Bright Colors below the floor) |

### Granting ELEVATED (one time)

Pick whichever you have. **ADB** (no extra app):

```bash
adb shell pm grant com.tideo.autobrightness android.permission.WRITE_SECURE_SETTINGS
```

- **Shizuku** — start the Shizuku app, then use the one-tap grant in Tideo's onboarding (Elevated step).
- **Root** — Tideo can run the same `pm grant` via `su` from the onboarding screen.

After the grant, Tideo writes the secure setting **directly** (no Shizuku binder needed for dimming).
The grant is detected on the next screen-on or when you re-open the app (it is *not* re-checked every
cycle, to save battery).

> **One runtime use of Shizuku.** Beyond the one-time grant, Shizuku is used at runtime in exactly one
> optional place: the **no-Location Wi-Fi SSID** context strategy runs `cmd wifi status` through
> Shizuku's shell so Wi-Fi-based context rules can read the SSID *without* the Location permission. It
> runs only when you create a Wi-Fi context rule and Shizuku is available; if Shizuku isn't running,
> Tideo silently falls back to the Location-based SSID path. The brightness pipeline itself never binds
> Shizuku.

## Troubleshooting

- **Stuck on a black/too-dark screen?** Flip the phone upside-down (charging port up) and shake it.
  Tideo answers with an SOS vibration and forces brightness to maximum. *(The gesture is suppressed
  for a few seconds right after you wake the screen, so grabbing the phone out of a pocket won't
  trigger it by accident.)*
- **Service stops adapting after a while.** Aggressive OEM battery management may kill the foreground
  service. Exempt Tideo from battery optimization; see [dontkillmyapp.com][dkma] for device-specific
  steps.
- **Super dimming doesn't visibly darken** on some OEM skins even when "ON". A few vendors rename or
  relocate the `reduce_bright_colors` secure keys — that's OEM variance, not a Tideo bug. Enable
  *Live Debug* (debug level 5) to confirm engagement.
- **Brightness range looks off.** Tideo normalizes the device's brightness range to a 0–255 scale; on
  unusual OEM ranges the mapping is auto-detected from `config_screenBrightnessSettingMaximum`.
- **Context rules not firing.** For per-app rules, grant Usage Access when prompted; for location/Wi-Fi
  rules, grant Location. Live Debug (level 8) shows the active context and any priority conflicts.

## Privacy

Tideo is local-first and has no analytics, ads, or accounts. It makes **one** outbound network
request, and only as a last resort:

- **IP-geolocation fallback (optional, cleartext).** Circadian scaling needs an approximate location
  to compute local sunrise/sunset. Tideo tries, in order: a fixed lat/lon you pin → the device's
  last-known location → a fresh GPS/network fix. Only if *all* of those are unavailable does it fall
  back to a single `GET http://ip-api.com/json` (the original AAB behaviour) to estimate your city
  from your public IP. This call is **cleartext HTTP** (ip-api.com's free tier has no HTTPS) and is
  scoped to that one host in `network_security_config.xml`. **You can turn it off** under
  **Circadian → Date & location → "IP-based location fallback"**; with it off, Tideo never contacts
  ip-api.com and simply waits for an on-device fix.

Everything else — the curve, smoothing, dimming, contexts — runs entirely on-device.

## Module layout

A 3-module Gradle build:

- **`:domain`** — pure JVM/Kotlin. All brightness math and decision logic (smoothing, curve mapping,
  thresholds, animation, circadian scale, context resolution). No Android deps; golden-tested.
- **`:platform`** — Android library. Real system adapters behind small interfaces: light sensor,
  brightness writer (OEM-range normalization), secure-dimming writer, tiered privilege manager,
  brightness-override observer, and battery/Wi-Fi/location/foreground-app readers.
- **`:app`** — Compose Material 3 UI, DataStore settings, the foreground monitoring service, a Quick
  Settings tile, a home-screen widget, a boot receiver, and the actionable notification.

## Building

Requires JDK 17+ (the toolchain targets 17) and the Android SDK (`local.properties` with `sdk.dir`,
or run `scripts/setup-android-sdk.sh`).

```bash
./gradlew :domain:test              # pure-JVM engine + golden parity tests
./gradlew :platform:test            # Robolectric adapter tests
./gradlew :app:testDebugUnitTest    # app unit + Robolectric tests
./gradlew :app:assembleDebug        # debug APK → app/build/outputs/apk/debug/
./gradlew build                     # everything: all modules, all tests, lint
```

## Project docs

The rebuild is driven by documents under `docs/rebuild/`:

- [`CLAUDE.md`](CLAUDE.md) — architecture, conventions, and the hard-won facts ledger.
- [`docs/rebuild/STATE.md`](docs/rebuild/STATE.md) — segment log, current state, deviations ledger.
- [`docs/rebuild/RUNBOOK.md`](docs/rebuild/RUNBOOK.md) — per-segment briefs.
- [`docs/rebuild/PARITY_CHECKLIST.md`](docs/rebuild/PARITY_CHECKLIST.md) — every Tasker artifact tracked
  to a disposition.
- [`docs/rebuild/DEVICE_TEST_SCRIPT.md`](docs/rebuild/DEVICE_TEST_SCRIPT.md) — the on-device acceptance
  script.

## Contributing

Tideo is the build artifact; the **[Advanced Auto Brightness][aab]** Tasker project is where the
brightness math and feature direction live.

- **App-layer / Android-Kotlin bug fixes are welcome here** as pull requests — crashes, OEM
  brightness/secure-key quirks, battery-saver kills, Compose/UI leaks, packaging. They live only in
  Tideo, so this is their home; PRs are triaged, not auto-closed.
- **Features and brightness-logic changes go to AAB** — open an issue there first (the math and golden
  fixtures are locked downstream).

See [`CONTRIBUTING.md`](CONTRIBUTING.md) and the **Bug report** issue template.

## Credits

- **Advanced Auto Brightness** (the original Tasker project this is rebuilt from) — by
  **/u/v_uurtjevragen**.
- **Tasker** — by João Dias (Tideo automates the same system settings natively; it is not affiliated
  with Tasker).

## License

[MIT](LICENSE) © 2026 /u/v_uurtjevragen.

## Status

`0.9.0` (acceptance candidate). Gates 1 & 2 passed on-device; **Gate 3** (the 24-hour soak in
[`docs/rebuild/DEVICE_TEST_SCRIPT.md`](docs/rebuild/DEVICE_TEST_SCRIPT.md)) is pending. Bumps to
`1.0.0` once Gate 3 passes.

[aab]: https://github.com/faded-penguin021/AdvancedAutoBrightness
[releases]: https://github.com/faded-penguin021/tideo-auto-brightness/releases
[dkma]: https://dontkillmyapp.com
