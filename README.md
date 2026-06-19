<!-- TODO(S14): bump versionCode + release-grade README -->
# Tideo Auto Brightness

Tideo is a modern Kotlin/Compose Android app that gives you fully manual control over your screen's
automatic brightness: a tunable lux→brightness curve, smooth animated transitions, circadian
(time-of-day) dimming, per-context overrides (battery / Wi-Fi / location / app), and an optional
privileged "super dimming" mode that takes the screen darker than the system minimum.

It is a ground-up rebuild of the **Advanced Auto Brightness** Tasker project, ported segment by
segment to native Kotlin with exact behavioural parity (the math and decision logic are golden-tested
against a transcription of the original Tasker engine).

## Module layout

The app is a 3-module Gradle build:

- **`:domain`** — pure JVM/Kotlin. All brightness math and decision logic (smoothing, curve mapping,
  thresholds, animation, circadian scale, context resolution). No Android dependencies; golden-tested.
- **`:platform`** — Android library. Real system adapters behind small interfaces: light sensor,
  brightness writer (with OEM-range normalization), secure-dimming writer, a tiered privilege manager,
  a brightness-override detector, and battery/Wi-Fi/location/foreground-app readers.
- **`:app`** — Compose Material 3 UI, DataStore-backed settings, the foreground monitoring service,
  a Quick Settings tile, a boot receiver, and the notification with inline actions.

## Privilege tiers

- **BASIC** — user-grantable `WRITE_SETTINGS`. The full core pipeline works (curve, animation,
  circadian, contexts).
- **ELEVATED** — `WRITE_SECURE_SETTINGS`, granted once via `pm grant` (adb / Shizuku / root). Unlocks
  super dimming. Shizuku is used only in the one-time grant flow, never as a runtime dependency.

## Building

Requires JDK 21 and the Android SDK (`local.properties` with `sdk.dir`, or run
`scripts/setup-android-sdk.sh`). Common commands:

```bash
./gradlew :domain:test              # pure-JVM engine + golden parity tests
./gradlew :platform:test            # Robolectric adapter tests
./gradlew :app:testDebugUnitTest    # app unit + Robolectric tests
./gradlew :app:assembleDebug        # debug APK → app/build/outputs/apk/debug/
```

`minSdk` 31, `target`/`compile` 35.

## Project docs

The rebuild is driven by documents under `docs/rebuild/`:

- [`CLAUDE.md`](CLAUDE.md) — architecture, conventions, and the hard-won facts ledger.
- [`docs/rebuild/STATE.md`](docs/rebuild/STATE.md) — segment log, current state, deviations ledger.
- [`docs/rebuild/RUNBOOK.md`](docs/rebuild/RUNBOOK.md) — per-segment briefs.

## Status

Pre-release; `versionCode = 1`, no public tag. Gate 2 closed, Gate 3 pending.
