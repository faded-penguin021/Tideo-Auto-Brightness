# defaults_audit.md — all 125 `%AAB_*` variables (S1)

Source of truth: **task570 _Initialize AAB Defaults_** (`extraction/tasks/task570_initialize-aab-defaults.md`), plus task637 _ProfileManager and task546 _Set Thresholds_ for vars not initialized in 570. Census: `grep -o %AAB_[A-Za-z0-9_]* | sort -u | wc -l` = **125** (confirms D-006).

**Classification counts:** 38 SETTING (user-facing) · 4 DERIVED (computed, persisted) · 83 RUNTIME (pipeline state). The 38 settings minus nothing ≈ matches CLAUDE.md "~37 user-facing settings" (off-by-one is `AAB_SetupTitle`, an onboarding UI string, arguably not a tunable).

## D-004 resolution (canonical values)

- **`%AAB_AnimSteps` = `20`** (task570 act26). This is the animation step count. **Missing from `AabSettings.kt`** — S8 must add it. The salvaged `AnimationConfig` defaults (50/5/30) are WRONG vs Tasker; canonical animation defaults are **AnimSteps=20, MinWait=25 ms, MaxWait=65 ms** (task570 act26/27/28). The AabSettings 25/65 (min/max wait) are correct; the step count 20 is the missing piece.
- **`%AAB_MaxSteps`** — appears in the variable census but is **never assigned a default in task570 (or 592/637)**. Treat as legacy/unused; do NOT invent a default. If S8 needs a max-step cap, derive from AnimSteps. (Logged in INDEX unresolved.)
- **`%AAB_ThreshMidpoint` = `log10(%AAB_Zone2End)` = `log10(10000)` = `4`** (task570 act39, DoMaths). DERIVED-but-persisted. Missing from `AabSettings.kt`; S8 should persist it or recompute from zone2End.
- **`%AAB_Throttle` = `AnimSteps*MaxWait+10` = `20*65+10` = `1310` ms** (task570 act29, DoMaths). Maps to `throttleDefaultMs`.
- **form2A/form2D/form3A are DERIVED** (continuity coefficients, task659) — correctly absent from `AabSettings.kt`. form1A/form2B/form2C ARE settings. Do not add form2A/3A as settings.

**Settings present in census but MISSING from `AabSettings.kt`:** `AAB_AnimSteps`, `AAB_ContextOverride` (per-profile override flag), `AAB_SetupTitle` (UI string), plus DERIVED `AAB_ThreshMidpoint`. → S8 schema-v2 work.

## Full table

| Variable | Default (task570 unless noted) | Class | In AabSettings.kt? | Meaning |
|---|---|---|---|---|
| `%AAB_ActiveContext` | — | RUNTIME | NO | Currently-applied context override id/name |
| `%AAB_AnimSteps` | 20 | SETTING | NO | User setting (see task570) |
| `%AAB_ChartJs` | — | RUNTIME | NO | Cached Chart.js HTML/asset blob |
| `%AAB_ContextCache` | — | RUNTIME | NO | Serialized context-evaluation cache (daily reset, prof8/task26) |
| `%AAB_ContextJSONCache` | — | RUNTIME | NO | JSON form of context cache |
| `%AAB_ContextLocMode` | — | RUNTIME | NO | Saved location-provider mode for context restore |
| `%AAB_ContextOverride` | true (per-profile, task637) | SETTING | NO | Per-profile flag: this profile applies an override |
| `%AAB_CurrentActiveProfile` | — | RUNTIME | NO | Name of active named profile |
| `%AAB_CurrentBright` | — | RUNTIME | NO | Last brightness shown (scene/notif readout) |
| `%AAB_CycleStart` | — | RUNTIME | NO | Animation cycle start timestamp |
| `%AAB_CycleTime` | — | RUNTIME | NO | Per-frame wait (ms) for current animation |
| `%AAB_CycleTotal` | — | RUNTIME | NO | Total cycle duration |
| `%AAB_Date` | — | RUNTIME | NO | Cached date for daily-rollover checks |
| `%AAB_Debug` | 0 | SETTING | yes | User setting (see task570) |
| `%AAB_DefaultThrottle` | — | RUNTIME | yes | Computed throttle window baseline (→ throttleDefaultMs) |
| `%AAB_DeltaFactor` | 1.8 | SETTING | yes | User setting (see task570) |
| `%AAB_DetectOverrides` | Off | SETTING | yes | User setting (see task570) |
| `%AAB_DimDynamic` | — | RUNTIME | NO | Runtime dynamic dimming amount |
| `%AAB_DimSpread` | 100 | SETTING | yes | User setting (see task570) |
| `%AAB_DimmingCurrent` | — | RUNTIME | NO | Current applied dimming level |
| `%AAB_DimmingDS` | — | RUNTIME | NO | Dimming dynamic-scale working value |
| `%AAB_DimmingEnabled` | false | SETTING | yes | User setting (see task570) |
| `%AAB_DimmingExponent` | 2.5 | SETTING | yes | User setting (see task570) |
| `%AAB_DimmingStatus` | — | RUNTIME | NO | 0/1 whether super-dimming engaged |
| `%AAB_DimmingStrength` | 25 | SETTING | yes | User setting (see task570) |
| `%AAB_DimmingStrengthCurr` | — | RUNTIME | NO | Current (animated) dimming strength |
| `%AAB_DimmingThreshold` | 15 | SETTING | yes | User setting (see task570) |
| `%AAB_EveningDuration` | — | RUNTIME | NO | Computed dusk ramp duration (task90) |
| `%AAB_EveningEnd` | — | RUNTIME | NO | Evening ramp end time (task90) |
| `%AAB_EveningStart` | — | RUNTIME | NO | Evening ramp start time (task90) |
| `%AAB_Form1A` | 5 | SETTING | yes | User setting (see task570) |
| `%AAB_Form2A` | =Form1A*sqrt(Zone1End) | DERIVED | NO |  |
| `%AAB_Form2B` | 8.8 | SETTING | yes | User setting (see task570) |
| `%AAB_Form2C` | 18 | SETTING | yes | User setting (see task570) |
| `%AAB_Form2D` | =Zone1End | DERIVED | NO |  |
| `%AAB_Form3A` | =continuity (task659) | DERIVED | NO |  |
| `%AAB_HTML_Graph` | — | RUNTIME | NO | Generated graph HTML (graph tasks) |
| `%AAB_HTML_Graph2` | — | RUNTIME | NO |  |
| `%AAB_HTML_Graph3` | — | RUNTIME | NO |  |
| `%AAB_HTML_Graph4` | — | RUNTIME | NO |  |
| `%AAB_HTML_Graph5` | — | RUNTIME | NO |  |
| `%AAB_HTML_Graph6` | — | RUNTIME | NO |  |
| `%AAB_HTML_Graph7` | — | RUNTIME | NO |  |
| `%AAB_HTML_Graph8` | — | RUNTIME | NO |  |
| `%AAB_HexOverlay` | — | RUNTIME | NO | Scene color overlay hex |
| `%AAB_Initializing` | — | RUNTIME | NO | true while init task618 running (gates override profile) |
| `%AAB_JavaDialogResponse` | — | RUNTIME | NO | Result of a Java-driven dialog |
| `%AAB_LastAnimation` | — | RUNTIME | NO | Timestamp/marker of last animation |
| `%AAB_LastRawLux` | — | RUNTIME | NO | Previous raw lux (smoothing par2) |
| `%AAB_LastSensorAccuracy` | — | RUNTIME | NO | Last %as_accuracy seen |
| `%AAB_Latitude` | — | RUNTIME | NO | User/last latitude (dynamic scale) |
| `%AAB_LocnBackOff` | — | RUNTIME | NO | Location listener backoff state |
| `%AAB_LocnListener_healthy` | — | RUNTIME | NO | Location listener health flag |
| `%AAB_LocnLog` | — | RUNTIME | NO | Location debug log |
| `%AAB_Longitude` | — | RUNTIME | NO | User/last longitude |
| `%AAB_MainLoop` | — | RUNTIME | NO | On/Off main-loop gate flag (prof760 gate) |
| `%AAB_Manual_Override` | — | RUNTIME | NO | true when user manually overrode brightness (pause) |
| `%AAB_MaxBright` | 255 | SETTING | yes | User setting (see task570) |
| `%AAB_MaxSteps` | — | RUNTIME | NO | Referenced but NEVER assigned a default in task570 — legacy/unused (see D-004 resolution) |
| `%AAB_MaxWait` | 65 | SETTING | yes | User setting (see task570) |
| `%AAB_MinBright` | 10 | SETTING | yes | User setting (see task570) |
| `%AAB_MinThrottle` | — | RUNTIME | NO | Referenced lower bound for throttle — not defaulted in 570 (runtime/legacy) |
| `%AAB_MinWait` | 25 | SETTING | yes | User setting (see task570) |
| `%AAB_MorningDuration` | — | RUNTIME | NO | Computed dawn ramp duration (task90) |
| `%AAB_MorningEnd` | — | RUNTIME | NO | Morning ramp end (task90) |
| `%AAB_MorningStart` | — | RUNTIME | NO | Morning ramp start (task90) |
| `%AAB_NetLocation` | — | RUNTIME | NO | Network-derived location for contexts |
| `%AAB_NextContextTime` | — | RUNTIME | NO | Next scheduled context evaluation time |
| `%AAB_NotifyUse` | — | RUNTIME | yes | Foreground notification enabled (set in task696 region, default true assumed) |
| `%AAB_NowSS` | — | RUNTIME | NO | Current seconds-of-day cache |
| `%AAB_Offset` | 0 | SETTING | yes | User setting (see task570) |
| `%AAB_Overrides` | — | RUNTIME | NO | Serialized manual-override curve points |
| `%AAB_PWMExp` | 0.8 | SETTING | yes | User setting (see task570) |
| `%AAB_PWMSensitive` | — | RUNTIME | yes | PWM-sensitive dimming toggle (set in dimming tasks ~L30069, not task570) |
| `%AAB_Package` | — | RUNTIME | NO | Foreground app package (context) |
| `%AAB_PermGranted` | — | RUNTIME | NO | Permission-granted flag |
| `%AAB_PolarState` | — | RUNTIME | NO | Polar day/night state (task90) |
| `%AAB_PrevBright` | — | RUNTIME | NO | Previous brightness for transition read-back |
| `%AAB_Privilege` | — | RUNTIME | NO | Detected privilege tier (None/Write Settings/Secure) |
| `%AAB_ProfileUser` | — | RUNTIME | NO | User-named profile selection |
| `%AAB_Proximity` | — | RUNTIME | NO | Proximity sensor state (prof759) |
| `%AAB_QSUse` | false | SETTING | yes | User setting (see task570) |
| `%AAB_ResumeTapped` | — | RUNTIME | NO | On when user tapped Resume in notification |
| `%AAB_Scale` | 1 | SETTING | yes | User setting (see task570) |
| `%AAB_ScaleDynamic` | — | RUNTIME | NO | Runtime dynamic scale multiplier (task90 output) |
| `%AAB_ScaleDynamicCompress` | — | RUNTIME | NO | Compressed dynamic scale (task548) |
| `%AAB_ScaleSpread` | 15 | SETTING | yes | User setting (see task570) |
| `%AAB_ScaleSteepness` | 6 | SETTING | yes | User setting (see task570) |
| `%AAB_ScaleTaperMidpoint` | 190 | SETTING | yes | User setting (see task570) |
| `%AAB_ScaleTaperSteepness` | 0.075 | SETTING | yes | User setting (see task570) |
| `%AAB_ScaleTransitionFactor` | 0.1 | SETTING | yes | User setting (see task570) |
| `%AAB_ScalingUse` | false | SETTING | yes | User setting (see task570) |
| `%AAB_Scenebg` | — | RUNTIME | NO | Scene background |
| `%AAB_SecondaryPrivilege` | — | RUNTIME | NO | Secondary privilege channel (Shizuku/root) |
| `%AAB_Service` | Off | SETTING | yes | User setting (see task570) |
| `%AAB_SettingsBGone` | — | RUNTIME | NO | Scene bg asset |
| `%AAB_SettingsBGtwo` | — | RUNTIME | NO | Scene bg asset |
| `%AAB_SetupComplete` | — | RUNTIME | NO | 1 once first-run init done (gates task570) |
| `%AAB_SetupTitle` | Advanced Auto Brightness Setup | SETTING | NO | Onboarding dialog title string |
| `%AAB_SunLastDate` | — | RUNTIME | NO | Date sun times last computed (task90 daily gate) |
| `%AAB_Sundawn` | — | RUNTIME | NO | Computed civil dawn |
| `%AAB_Sundusk` | — | RUNTIME | NO | Computed civil dusk |
| `%AAB_Sunlightduration` | — | RUNTIME | NO | Daylight minutes (polar test vs 1380) |
| `%AAB_Sunnoon` | — | RUNTIME | NO | Solar noon |
| `%AAB_Sunrise` | — | RUNTIME | NO | Sunrise time |
| `%AAB_Sunset` | — | RUNTIME | NO | Sunset time |
| `%AAB_Test` | — | RUNTIME | NO | Debug/test scratch var |
| `%AAB_ThreshAbsHigh` | — | RUNTIME | NO | Absolute upper lux gate (prof760) — set by task546 Set Thresholds, NOT task570 |
| `%AAB_ThreshAbsLow` | — | RUNTIME | NO | Absolute lower lux gate (prof760) — set by task546 Set Thresholds, NOT task570 |
| `%AAB_ThreshBright` | 0.08 | SETTING | yes | User setting (see task570) |
| `%AAB_ThreshDark` | 0.3 | SETTING | yes | User setting (see task570) |
| `%AAB_ThreshDim` | 0.25 | SETTING | yes | User setting (see task570) |
| `%AAB_ThreshDynamic` | 5 | SETTING | yes | User setting (see task570) |
| `%AAB_ThreshMidpoint` | =log10(Zone2End) (=4) | DERIVED | NO |  |
| `%AAB_ThreshSteepness` | 2.1 | SETTING | yes | User setting (see task570) |
| `%AAB_Throttle` | =AnimSteps*MaxWait+10 (=1310) | SETTING | yes | User setting (see task570) |
| `%AAB_TrustUnreliable` | Off | SETTING | yes | User setting (see task570) |
| `%AAB_Version` | — | RUNTIME | NO | Project version string |
| `%AAB_Zone1End` | 35 | SETTING | yes | User setting (see task570) |
| `%AAB_Zone2End` | 10000 | SETTING | yes | User setting (see task570) |
| `%AAB_calc_dawn` | — | RUNTIME | NO | task90 working: calc dawn |
| `%AAB_calc_dusk` | — | RUNTIME | NO | task90 working: calc dusk |
| `%AAB_calc_noon` | — | RUNTIME | NO | task90 working: calc noon |
| `%AAB_calc_sunrise` | — | RUNTIME | NO | task90 working: calc sunrise |
| `%AAB_calc_sunset` | — | RUNTIME | NO | task90 working: calc sunset |
