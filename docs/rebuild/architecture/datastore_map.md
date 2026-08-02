# DataStore, personal-data, and backup map (DA-034)

The rebuild deliberately uses **nine independent DataStores** instead of one mega-store. Each owns a
single cohesive concern with its own lifetime, write cadence and failure mode, so a corrupt or
schema-drifted file in one never takes down the others. They are declared in
`app/.../storage/AppDataStores.kt`.

| # | Store (extension) | File | Type | Payload | Schema ver | Serializer | Why independent |
|---|---|---|---|---|---|---|---|
| 1 | `settingsDataStore` | `aab_settings.json` | typed JSON | `AabSettings` (flat, 48 fields) | **3** (`CURRENT_SCHEMA_VERSION`) | `AabSettingsSerializer` | The core tuned curve/threshold/dimming parameters; versioned + migrated (v1→v2→v3; the 7 D-151/D-152 display-toggle fields joined additively at v3). High read frequency (every pipeline reapply). |
| 2 | `serviceHealthDataStore` | `service_health.preferences_pb` | Preferences | heartbeat timestamps, degraded flag/reason | n/a (schema-less) | — (Preferences) | Volatile runtime diagnostics written by `MaintenanceWorker`; losing it is harmless. |
| 3 | `experimentPrefsDataStore` | `experiment_prefs.preferences_pb` | Preferences | fixed date + lat/lon override, geo-IP opt-in, daily cached lat/lon/day | n/a (schema-less) | — (Preferences) | Circadian experiment and acquisition state. It contains precise coordinates and a privacy consent choice. |
| 4 | `contextRulesDataStore` | `aab_context_rules.json` | typed JSON | `ContextOverrideConfig` (rule list) | **1** (`ContextOverrideConfig.SCHEMA_VERSION`) | `ContextRulesSerializer` | The context-override rule set; edited from the Contexts UI, read by `ContextEngine`. Tasker-JSON interop lives here, separate from settings. |
| 5 | `overridePointsDataStore` | `aab_override_points.json` | typed JSON | `OverridePoints` (≤50 records) | **1** (`OverridePoints.SCHEMA_VERSION`) | `OverridePointsSerializer` | Runtime-captured manual-override training points for the curve wizard; append-mostly, capped at 50. Distinct lifetime from user settings. |
| 6 | `userProfilesDataStore` | `aab_user_profiles.json` | typed JSON | `SavedProfiles` (named profiles) | **1** (`SavedProfiles.SCHEMA_VERSION`) | `SavedProfilesSerializer` | User-editable named profiles (built-ins seeded once). Context rules target these by name; kept apart so a profile-store problem can't corrupt the live settings. |
| 7 | `powerDrawDataStore` | `power_draw.preferences_pb` | Preferences | measured OLED current/power samples (task524) | n/a (schema-less) | — (Preferences) | Disposable device-specific diagnostic/calibration data. |
| 8 | `controlPrefsDataStore` | `control_prefs.preferences_pb` | Preferences | `external_control_enabled` (opt-in gate) | n/a (schema-less) | — (Preferences) | **D-157**: the device-local consent flag for the external intent-control surface. Its OWN store — deliberately **not** an `AabSettings` field — so profile apply/import/reset can never flip it. |
| 9 | `contextBaselineDataStore` | `aab_context_baseline.json` | typed JSON | `ContextBaseline` (nullable `AabSettings` snapshot + `userProfileName`) | **2** (`ContextBaseline.SCHEMA_VERSION`) | `ContextBaselineSerializer` | **D-170**: the pre-override baseline snapshot (Tasker task626 `_ContextResume` / `%AAB_ProfileUser` revert file). Context-rule loads write through to store 1; this holds what the no-match revert restores. Kept apart so the snapshot/clear cadence and a corrupt snapshot (degrades to "no revert reference") never touch the live settings file. **DA-018**: v2 added `userProfileName` — the persisted `%AAB_ProfileUser` name (the last manually-loaded profile, default `"Default"`), the no-match/Resume revert TARGET. It outlives the snapshot (a snapshot `clear()` preserves it via `copy`). Additive → v1 files decode with the default; no migration hook reads the constant. |

## Versioning policy

- Each **typed-JSON** store declares a schema-version constant. `DataStoreSchemaVersionTest` asserts the
  constant matches the serializer's `defaultValue` (and, for `settings`, the model default).
- `settings` is the only store with an in-payload `schemaVersion` field and a real migration chain
  (D-008/G2R-F85). The other typed stores evolve additively (new fields with defaults, read with
  `ignoreUnknownKeys = true`), so no migration is needed: `contextRules`/`overridePoints`/`userProfiles`
  stay at **v1**, and `contextBaseline` is at **v2** (DA-018 added `userProfileName` additively — the
  constant was bumped for honesty, not a migration). Bump the `SCHEMA_VERSION` constant and add a real
  migration only on a *breaking* shape change.
- The four **Preferences** stores (`service_health`, `experiment_prefs`, `power_draw`, `control_prefs`)
  are intentionally schema-less key/value bags (no serializer, no version); they hold disposable/optional
  data.

## Why not one store

A single store would couple unrelated write cadences (every-cycle health vs. rare profile edits),
make one corrupt section fatal to all settings, and force one monolithic migration. The split keeps each
concern's blast radius local — the guiding principle for this map.

## Personal-data inventory

The stores are app-private (ordinary apps cannot read them), but app-private does not mean suitable
for cloud backup:

- **Location:** `experiment_prefs.preferences_pb` contains user-pinned and daily cached latitude /
  longitude. `aab_context_rules.json` can contain geofence centers/radii.
- **Wi-Fi SSIDs and foreground-app identities:** only user-authored rule targets in
  `aab_context_rules.json` are persisted. Live SSID and foreground package signals stay in memory;
  raw `cmd wifi status` / `dumpsys wifi` output is parsed, bounded, and discarded.
- **Names:** `aab_user_profiles.json` contains user-chosen profile names/settings;
  `aab_context_rules.json` contains rule/profile names; `aab_context_baseline.json` contains the last
  manually loaded profile name. These can reveal routines even without sensor values.
- **Behavioral/system diagnostics:** `aab_override_points.json` contains learned lux/brightness
  pairs; `power_draw.preferences_pb` contains device power measurements;
  `service_health.preferences_pb` contains timestamps and degraded reasons. `files/crash/*.txt`
  contains exception messages/stacks that may incidentally include URIs, package names or system
  state. None is transmitted by the app; crash text leaves only after the user copies it.
- **Profile documents:** user-selected SAF imports remain with their document provider. Historical
  `exportToAppPrivate` output can create root `files/*.json` profile copies. Their content and names
  are treated as user data, not diagnostics.

## Cloud-backup and device-transfer decisions

`data_extraction_rules.xml` is an **allowlist**, so unlisted present and future files do not silently
enter backup. DataStore files resolve under the `file` domain at `datastore/<name>`.

| Category / file | Cloud backup | Device transfer | Decision |
|---|---:|---:|---|
| Core settings (`aab_settings.json`) | **Yes** | **Yes** | User configuration is the expected restore payload; it contains no location, SSID, package identity or free-form profile name. It includes the default-on `serviceEnabled` operational state and other feature toggles. That state is deliberately portable (a fresh install also starts enabled), unlike the separate default-off geo-IP/external-control privacy consents. |
| Named profiles (`aab_user_profiles.json`) | **Yes** | **Yes** | User-created work worth restoring. Names may be personal. Each record serializes a full `AabSettings` snapshot—including global fields such as service/debug/notification state—even though `ProfileApplier` preserves those globals rather than applying them. No location, SSID or app identity is present. |
| Context rules (`aab_context_rules.json`) | **No** | **Yes** | Excluded from third-party cloud because it may combine coordinates, SSIDs, package identities and routine names. Included in direct device transfer because the user explicitly migrates devices and the rules are otherwise costly to recreate. Runtime permission/app-op grants still do not transfer. |
| Circadian experiment/cache (`experiment_prefs.preferences_pb`) | **No** | **No** | Precise coordinates, cached acquisition state and the geo-IP opt-in share one file. Fail closed rather than transfer stale location or consent; circadian reacquires locally and geo-IP returns off. |
| Context baseline (`aab_context_baseline.json`) | **No** | **No** | Derived restoration snapshot and profile name can be stale without the original runtime context. It is rebuilt as contexts run. |
| Override points (`aab_override_points.json`) | **No** | **No** | Behavioral learning data (lux/brightness history), not required configuration. The new device learns fresh points. |
| Service health (`service_health.preferences_pb`) | **No** | **No** | Volatile timestamps/reasons are diagnostics and meaningless after restore. |
| Power draw (`power_draw.preferences_pb`) | **No** | **No** | Device-specific measurements must not be applied to different hardware. |
| External-control gate (`control_prefs.preferences_pb`) | **No** | **No** | Consent is device-local and defaults off after restore/transfer; a migrated install must explicitly re-enable its exported control surface. |
| Crash files (`crash/`) | **No** | **No** | Sensitive diagnostics are local-only and irrelevant on a replacement/restored install. |
| App-private exported profiles (`files/*.json`) | **No** | **No** | Redundant copies with user-chosen names/content; users migrate explicitly exported SAF documents themselves. |

Android runtime grants, Usage Access, Accessibility enablement, Shizuku/root state, and persisted SAF
URI grants are OS/provider capabilities rather than these payloads; the app re-probes them and does
not treat restored settings as proof of permission.
