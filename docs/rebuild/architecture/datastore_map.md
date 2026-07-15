# DataStore map (S12.9c #5)

The rebuild deliberately uses **nine independent DataStores** instead of one mega-store. Each owns a
single cohesive concern with its own lifetime, write cadence and failure mode, so a corrupt or
schema-drifted file in one never takes down the others. They are declared in
`app/.../storage/AppDataStores.kt`.

| # | Store (extension) | File | Type | Payload | Schema ver | Serializer | Why independent |
|---|---|---|---|---|---|---|---|
| 1 | `settingsDataStore` | `aab_settings.json` | typed JSON | `AabSettings` (flat, 48 fields) | **3** (`CURRENT_SCHEMA_VERSION`) | `AabSettingsSerializer` | The core tuned curve/threshold/dimming parameters; versioned + migrated (v1→v2→v3; the 7 D-151/D-152 display-toggle fields joined additively at v3). High read frequency (every pipeline reapply). |
| 2 | `serviceHealthDataStore` | `service_health` | Preferences | heartbeat/telemetry key-values | n/a (schema-less) | — (Preferences) | Volatile runtime health written by `MaintenanceWorker`; losing it is harmless, so it stays a cheap key/value store with no schema. |
| 3 | `experimentPrefsDataStore` | `experiment_prefs` | Preferences | fixed date + lat/lon override | n/a (schema-less) | — (Preferences) | Circadian "Experiment" overrides (`%AAB_Date`/`Latitude`/`Longitude`); rarely set, optional, key/value. |
| 4 | `contextRulesDataStore` | `aab_context_rules.json` | typed JSON | `ContextOverrideConfig` (rule list) | **1** (`ContextOverrideConfig.SCHEMA_VERSION`) | `ContextRulesSerializer` | The context-override rule set; edited from the Contexts UI, read by `ContextEngine`. Tasker-JSON interop lives here, separate from settings. |
| 5 | `overridePointsDataStore` | `aab_override_points.json` | typed JSON | `OverridePoints` (≤50 records) | **1** (`OverridePoints.SCHEMA_VERSION`) | `OverridePointsSerializer` | Runtime-captured manual-override training points for the curve wizard; append-mostly, capped at 50. Distinct lifetime from user settings. |
| 6 | `userProfilesDataStore` | `aab_user_profiles.json` | typed JSON | `SavedProfiles` (named profiles) | **1** (`SavedProfiles.SCHEMA_VERSION`) | `SavedProfilesSerializer` | User-editable named profiles (built-ins seeded once). Context rules target these by name; kept apart so a profile-store problem can't corrupt the live settings. |
| 7 | `powerDrawDataStore` | `power_draw` | Preferences | measured OLED power-draw dataset (task524) | n/a (schema-less) | — (Preferences) | The Tools power-draw calibration samples; overwritten on recalibration, disposable, key/value. Distinct lifetime from settings (S14). |
| 8 | `controlPrefsDataStore` | `control_prefs` | Preferences | `external_control_enabled` (opt-in gate) | n/a (schema-less) | — (Preferences) | **D-157**: the opt-in flag for the external intent-control surface. Its OWN store — deliberately **not** an `AabSettings` field — so profile apply/import chokepoints (`applyProfile`/`replaceAll`/`resetDefaults`/legacy import) can never flip it. Single Boolean, key/value. |
| 9 | `contextBaselineDataStore` | `aab_context_baseline.json` | typed JSON | `ContextBaseline` (nullable `AabSettings` snapshot) | **1** (`ContextBaseline.SCHEMA_VERSION`) | `ContextBaselineSerializer` | **D-170**: the pre-override baseline snapshot (Tasker task626 `_ContextResume` / `%AAB_ProfileUser` revert file). Context-rule loads write through to store 1; this holds what the no-match revert restores. Kept apart so the snapshot/clear cadence and a corrupt snapshot (degrades to "no revert reference") never touch the live settings file. |

## Versioning policy

- Each **typed-JSON** store declares a schema-version constant. `DataStoreSchemaVersionTest` asserts the
  constant matches the serializer's `defaultValue` (and, for `settings`, the model default).
- `settings` is the only store with an in-payload `schemaVersion` field and a real migration chain
  (D-008/G2R-F85). The other three typed stores are at **v1**: their `@Serializable` payloads evolve
  additively (new fields with defaults, read with `ignoreUnknownKeys = true`), so no migration is needed
  yet. Bump the `SCHEMA_VERSION` constant and add a migration only on a *breaking* shape change.
- The four **Preferences** stores (`service_health`, `experiment_prefs`, `power_draw`, `control_prefs`)
  are intentionally schema-less key/value bags (no serializer, no version); they hold disposable/optional
  data.

## Why not one store

A single store would couple unrelated write cadences (every-cycle health vs. rare profile edits),
make one corrupt section fatal to all settings, and force one monolithic migration. The split keeps each
concern's blast radius local — the guiding principle for this map.
