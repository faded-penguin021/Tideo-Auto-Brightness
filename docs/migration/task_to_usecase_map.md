# Tasker → Kotlin domain use-case mapping

This document maps key Tasker tasks/profiles in `Advanced_Auto_Brightness_V3.3.prj_4.xml` to the new Kotlin architecture.

## Module boundaries

- `app`: entry points and dependency graph composition (`AppModule`, `Main`).
- `domain`: policy engine + use-cases + interfaces (ports).
- `data`: persistence and import/export adapters.
- `platform`: Android/tasker-facing adapters (sensor, overlay, usage stats, system brightness).

## Core mapping

| Tasker task/profile | New module | Domain use-case / service | Notes |
|---|---|---|---|
| `Monitor Ambient Light` + `Process Sensor Event (Java)` + `Lux Smoothing (Java)` | `platform` + `domain` | `AmbientLuxSource` + `EvaluateAndApplyBrightnessUseCase` | Sensor capture remains platform; smoothing/evaluation logic migrates into domain policy/use-case flow. |
| `Evaluate Light Change (Java) V2` + `Dynamic Range Compressed Scale (Java) V2` | `domain` | `BrightnessPolicyEngine.computeTarget()` | Brightness decision logic centralized in pure Kotlin engine for testability. |
| `Advanced Auto Brightness` (main orchestrator) | `app` | `EvaluateAndApplyBrightnessUseCase.run()` | Main orchestration moved to app composition root + use-case invocation. |
| `Manual Override` + `Allow Override` + `Resume After Override` | `domain` + `platform` | `PermissionStateProvider`, `OverlayController` and future `HandleManualOverrideUseCase` | Permission/overlay concerns abstracted behind domain ports. |
| `Context: App Changed`, `Context: Time Changed`, `Context: Battery Changed`, `Context: Location Changed` | `platform` + `domain` | `ContextProvider.currentContext()` | Context aggregation happens in platform adapter, consumed by domain engine. |
| `Reset Brightness and State` + `Panic (Reset)` | `domain` + `data` | future reset use-case + `SettingsRepository` | Reset behavior should be codified as a dedicated use-case using repository-backed state. |
| `_ForegroundNotification`, `Repost Foreground Notification`, `Repost Paused Notification` | `platform` | `OverlayController` | Notification/overlay duties move to a dedicated platform adapter. |
| `Initialize AAB Defaults` + `Throttle Reinitialization` | `data` + `domain` | `SettingsRepository`, `SettingsFileAdapter` | Defaults and persisted runtime params become structured settings data. |

## Interface placement checklist

The following interfaces are defined in `domain` and implemented in `platform`:

- `AmbientLuxSource`
- `BrightnessApplier`
- `ContextProvider`
- `PermissionStateProvider`
- `OverlayController`

This keeps domain independent of Android APIs while preserving behavior parity during migration.
