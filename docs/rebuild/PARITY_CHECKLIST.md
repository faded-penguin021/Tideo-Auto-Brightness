# PARITY_CHECKLIST — every Tasker artifact, tracked to disposition

Statuses: `pending` → `ported` (with target) / `dropped(reason)` → `device-verified` (human, Gates).
Segments flip rows they own; S14 enforces zero `pending`; the human flips to `device-verified`.
XML anchors: profiles/scenes verified line numbers (see XML_RECIPES.md R4/R5/R7); task anchors
filled by S1/S2 during extraction.

## Profiles (18)

> S1 extracted profiles 753–761, 769 → `extraction/profiles.md` (context+gate+enter/exit+pri). Profiles 762–768, 8 are S2. Status stays `pending` until ported to Kotlin (S9+).

| Profile | XML | Ported to | Status |
|---|---|---|---|
| prof753 Hibernate (Display Off) → task585 | L3 | runtime/BrightnessPipelineController.hibernate (S9a) | ported |
| prof754 Throttle Reinitialization → task566 | L16 | runtime/BrightnessPipelineController.reinit (S9a) | ported |
| prof755 Allow Override → task567 | L56 | runtime/OverrideMonitor + ProfileGates.allowOverrideGate (S9a) | ported |
| prof756 Repost Paused Notification → task567 | L111 | runtime/AmbientMonitoringService paused notification (S9a) | ported |
| prof757 Repost Foreground Notification → task584 | L156 | runtime/AmbientMonitoringService live notification (S9a) | ported |
| prof758 Dynamic Scale Engine → task90 | L195 | gate transcribed: ProfileGates.dynamicScaleGate + truth-table test (S9a); task90 scheduling S9b/S12 | partial (gate S9a) |
| prof759 Proximity Detection → task545 | L300 | | pending |
| prof760 Monitor Ambient Light → task554 (incl. ConditionList gate) | L318 | ProfileGates.monitorAmbientLightGate + truth-table test + BrightnessPipelineController (S9a) | ported |
| prof761 Initialize (Display On) → task618 | L386 | runtime/BrightnessPipelineController.reinit/setInitialBrightness (S9a) | ported |
| prof762 Context: App Changed → task43 | L398 | runtime/ContextEngine (S10 — foreground-app poll → APP_CHANGED veto → ContextOverrideResolver); AndroidContextSignalSource | ported (engine; rule UI S12) |
| prof763 Context: Battery Changed → task43 | L456 | runtime/ContextEngine (S10 — BatteryStateReader flow → BATTERY veto Δ≥5%/plug-flip → resolver) | ported (engine; rule UI S12) |
| prof764 Context: Time Changed → task43 | L500 | runtime/ContextEngine (S10 — time-window eval on pipeline ticks; nextContextTime computed by resolver) | ported (engine; rule UI S12) |
| prof765 Context: Location Listener → task630 | L541 | runtime/AndroidContextSignalSource LocationReader (passive last-known); persistent listener/backoff (task630/631) not ported — coarse passive read only (D-042) | partial (passive location; listener S12/S14) |
| prof766 Context: Location Refresher → task631 | L579 | not ported — adaptive location refresher/zombie-listener watchdog out of S10 scope (D-042) | deferred (S12/S14) |
| prof767 Context: Location Changed → task43 | L628 | runtime/ContextEngine (S10 — LOCATION caller evaluates when a rule uses [LOC]) | ported (engine; rule UI S12) |
| prof768 Context: WiFi (Dis)connected → task43 | L676 | runtime/ContextEngine (S10 — WifiInfoReader SSID flow → WIFI veto on SSID change → resolver) | ported (engine; rule UI S12) |
| prof769 Panic (Reset) → task528 | L722 | runtime/BrightnessPipelineController.panic + notification action (S9a) | ported |
| prof8 Context: Reset Serialized Cache → task26 | L744 | N/A — DataStore is the single fresh source of truth; the RAM/disk cache reset failsafe is moot (D-042) | dropped (cache obsolete) |

## Pipeline & feature task clusters (~25)

> S1 transcribed the core-pipeline tasks (554, 544, 535, 546, 661, 548, 659, 543, 696, 698, 700, 618, 585, 566, 567, 569, 561, 528, 570, 645–654, 644, 551, 584) → `extraction/tasks/*.md` + `pipeline_spec.md` + `defaults_audit.md`. Status stays `pending` until ported (S4–S9). Non-pipeline clusters (90, 43/contexts, 38/655, 524, 592/637/622, debug, misc UI) are S2.

| Cluster (tasks) | XML | Ported to | Status |
|---|---|---|---|
| Sensor ingest: 554 Process Sensor Event | | TaskerReference (S4) + BrightnessEngine.kt (S5) | ported |
| Light-change eval + thresholds: 544, 546 | | TaskerReference + threshold.csv (S4) + BrightnessEngine.absoluteThresholds/dynamicThreshold (S5) | ported |
| Lux smoothing: 535 | | TaskerReference + smoothing.csv (S4) + BrightnessEngine.smoothLux (S5) | ported |
| Lux→brightness mapping: 661 (+663 Java cross-check) | | TaskerReference + mapping.csv (S4) + BrightnessEngine.mapLuxToBrightness (S5) | ported |
| Compressed dynamic scale: 548 | | TaskerReference + taper.csv (S4) + BrightnessEngine.compressedDynamicScale (S5) | ported |
| Continuity coefficients: 659 _UpdateBrightnessFormulae | | TaskerReference + formulae.csv (S4) + BrightnessFormulae.kt (S5) | ported |
| Animation calc: 543 | | TaskerReference + animation.csv (S4) + BrightnessEngine.calculateAnimation (S5) | ported |
| Brightness transitions: 696, 698 | | TaskerReference + transition.csv/dimming.csv (S4); runtime/AnimationRunner per-frame write + read-back override detect (S9a). S9b wires the ELEVATED secure-dimming (reduce_bright_colors) layer; the task698 DC-like *unprivileged overlay* transition (653/654) is deferred to S12 (D-040) | ported (brightness + secure dimming; unprivileged overlay S12) |
| Software/super dimming math: 700, 645, 646, 647 | | SoftwareDimming.kt (S5 math, golden-tested superdimming.csv 2016 rows, CorePipelineParityTest); runtime/SuperDimmingCoordinator (S9b) engages task646 dimShell → AndroidSecureDimmingController (reduce_bright_colors) when ELEVATED, disengages above threshold (task645); wired into pipeline cycle + pause/override/panic/hibernate; SuperDimmingCoordinatorTest tier-gated | ported (math + secure-dimming wiring) |
| Initial brightness on wake: 618 | | TaskerReference (S4) + InitialBrightness.kt (S5) + InitialBrightnessTest.kt (S5) | ported |
| Hibernate/reset: 585 | | runtime/BrightnessPipelineController.hibernate (S9a — sensor unregister + runtime-state clear) | ported |
| Throttle reset: 566 | | runtime/BrightnessPipelineController.reinit (S9a — throttle = settings default on wake) | ported |
| Manual override detect/resume: 567, 569, 561, 640, 641, 636 | | OverrideRules.kt (S5 pure logic, OverrideRulesTest.kt S5); runtime wiring: OverrideMonitor + controller pause/resume + recordOverridePoint (S9a). 640/641/636 override-array CRUD UI = S12 | ported (detect/pause/resume) |
| Panic reset: 528 | | runtime/BrightnessPipelineController.emergencyStop + notification Reset action; Gate-1 G1-F4 fix: panic is a FULL STOP (restore 255 + drop dimming + %AAB_Service=Off + service teardown), not a pausable state (D-041) | ported |
| Init/defaults: 570 Initialize AAB Defaults | | | pending |
| Circadian dynamic scale: 90 (+ polar handling) | | SolarCalculator.kt + DynamicScaleEngine.kt (S6 domain, golden-tested circadian.csv 576 rows, CircadianParityTest + 4 polar assertions); BrightnessEngine delegates computeDynamicScale (D-031) | ported |
| Context evaluation: 43, 623, 624, 625, 626, 628, 630, 631, 633, 105, 26 | | S10: ContextOverrideResolver (task43 PASS3/4, 21-case matrix test) + ContextEngine (PASS1 cooldown/PASS2 veto) + ContextRuleStore (task623 upsert/delete CRUD); 624/630/631 location-listener subsystem deferred (D-042) | ported (eval+CRUD; location-listener S12/S14) |
| Privilege detection/grant: 378, 643, 563 | | S2 extracted → features_spec.md; platform layer: AndroidPrivilegeManager + ShizukuGrantGateway (S7); S11 UI: OnboardingScreen stepper (POST_NOTIFICATIONS → WRITE_SETTINGS re-check → optional ELEVATED with adb-copy/Shizuku/root + usage-access) and Dashboard tier badge; Shizuku user-service `pm grant` exec completed (AIDL IShizukuUserService, closes D-032) | ported (detect + grant UI + Shizuku exec) |
| QS tile: 551, 552 | | S2 extracted → features_spec.md; runtime/BrightnessTileService (S9b — toggles serviceEnabled + start/stop FGS via AutoBrightnessRuntime; manifest QS_TILE entry + BIND_QUICK_SETTINGS_TILE); BrightnessTileServiceTest instantiation smoke | ported (toggle) |
| Foreground notification: 584, 692 | | S2 extracted → features_spec.md; runtime/AmbientMonitoringService live lux/target notification + Pause/Resume/Reset/Disable actions (S9a) | ported |
| Curve suggestion wizard: 38, 655 | | CurveSuggestionEngine.kt (S6 domain, golden-tested wizard.csv 12 rows, WizardParityTest); applyToLiveCurve = task655 | ported |
| Formula validation: 583, 707 | | S2 extracted → features_spec.md; SettingsValidator.kt (S8 — 5 rules: form2A<0, form3A<0, form2C>zone1End advisory + predicted-brightness@1000lux<25 safety) | ported |
| Power draw calibration: 524 | | S2 extracted → features_spec.md; Tools entry + PowerDrawChart slot (S12); on-device current-sampling measurement deferred (D-044) | partial (UI entry S12; measurement deferred) |
| Profiles/import/export/defaults: 592, 637, 622 | | DefaultProfiles.kt (S8); AabSettings v2 + migration + TaskerLegacyProfileSerializer (S8); ContextRuleStore (S10); Profiles screen (S12 — apply/reset + CreateDocument/OpenDocument import-export incl. legacy) | ported |
| Debug tooling: 634, 635 | | Tools screen 10-label debug selector (S12, D-023); in-app log view deferred (D-044) | partial (selector S12; log view deferred) |
| Misc UI plumbing tasks (scene-resize 620, exits 656, toggles 547/553/555/558/560/576/587/589/638/648/649, chartjs cache 581, logo 619, color 639/379/579/652, about/license/guide 380/401/512, updates 706, experiments 540/541/542/381/382) | | S2 extracted → screen_map.md (scene dispositions) | pending |
| **Anonymous scene-handler tasks (168 unnamed**, incl. 34 `keyTask` back-key handlers) | various | S3.5 census + S12 Step-0 triage (a/b/c buckets) → extraction/tasks/anonymous_handlers.md | ported (S12 — (a)/(b) dropped w/ shared reasons, (c) ported into 7 screens; chart-gen rows deferred-S13) |

## Scenes (20) → M3 screens (~9, per S2 screen_map.md)

> S2 extracted all 20 scenes → `extraction/scenes/*.md` (450-element tables) + `screen_map.md` (full disposition matrix). 'Ported to' = target M3 screen; Status `pending` until rendered (S11–S13).

| Scene | XML | Ported to | Status |
|---|---|---|---|
| AAB Menu | L4462 | Dashboard (M3 nav) | ported (S11 — M3 nav shell + live Dashboard; nav to all target screens) |
| AAB Brightness Settings | L1415 | Curve & Brightness | ported (S12 — fields + validator + live form2A/3A + BrightnessCurveChart) |
| AAB Reactivity Settings | L6739 | Reactivity | ported (S12 — thresholds + DetectOverrides/trust toggles; chart slot S13) |
| AAB Superdimming Settings | L7533 | Animation & Dimming | ported (S12 — anim + ELEVATED-gated dimming + PWM; chart slot S13) |
| AAB Misc Settings | L4718 | Dashboard/Tools | ported (S12 — anim/throttle in Animation & Dimming, debug/notify in Tools/Dashboard) |
| AAB Experiment Settings | L3334 | Dynamic Scale | ported (S12 — scaling/taper fields + warnings; chart slot S13) |
| AAB Profile | L5724 | Profiles & Import/Export | ported (S12 — built-in profiles + reset + JSON/legacy import-export + context CRUD) |
| AAB Debug Scene | L2583 | Tools | ported (S12 — 10-label debug selector + wizard + calibration entry) |
| AAB Color Filter | L2552 | Animation & Dimming | ported (S12 — PWM-sensitive + exponent rows) |
| AAB Brightness Graph | L1202 | Curve & Brightness (BrightnessCurveChart) | ported (S12 — BrightnessCurveChart = chart template; ChartCanvas engine) |
| AAB Alpha Graph | L1038 | Reactivity (alpha overlay) | partial (S12 host slot; chart render S13) |
| AAB Reactivity Graph | L6563 | Reactivity (ReactivityChart) | partial (S12 host slot; chart render S13) |
| AAB Dimming Graph | L3006 | Animation & Dimming (DimmingChart) | partial (S12 host slot; chart render S13) |
| AAB Circadian Dimming Graph | L2388 | Animation & Dimming (CircadianChart; re-homed S3.5/D-026, visible only when %AAB_ScalingUse on) | partial (S12 host slot; chart render S13) |
| AAB Taper Graph | L8387 | Dynamic Scale (TaperChart) | partial (S12 host slot; chart render S13) |
| AAB Power Draw Graph | L5611 | Tools (PowerDrawChart) | partial (S12 host slot; chart + on-device calibration S13/Gate) |
| AAB Experiment Graph | L3170 | Dynamic Scale (ExperimentChart) | partial (S12 host slot; chart render S13) |
| AAB About | L799 | About+Guide+Onboarding | partial (S11 — onboarding/privilege stepper done; About content S13) |
| AAB User Guide | L8551 | About+Guide+Onboarding | partial (S11 — onboarding done; User Guide content S13) |
| AAB Chart.Js License | L2194 | dropped(Chart.js removed) | pending (S2 extracted) |

## Java blocks (40, anchors verified — see XML_RECIPES.md R7 for the full line↔task table)

| Block (task · line) | Extracted (S1/S2) | Reference impl (S4/S6) | Production port | Status |
|---|---|---|---|---|
| task105 L8906 · _GetWifiNoLocation | ✓ S1 | | | pending |
| task378 L9468 · _PrivilegeDetection | ✓ S1 | | | pending |
| task38 L9921 · _SuggestCurveParameters | ✓ S1 | ✓ S6 (delegate) | CurveSuggestionEngine.kt (S6) | ported |
| task43 L12091 · _EvaluateContexts | ✓ S1 | | domain/context/ContextOverrideResolver.kt + app/runtime/ContextEngine.kt (S10) | ported |
| task524 L14246 · _CalibratePowerDraw | ✓ S1 | | | pending |
| task535 L15204 · Lux Smoothing | ✓ S1 | ✓ S4 | BrightnessEngine.smoothLux (S5) | ported |
| task543 L15878 · Calculate Animation | ✓ S1 | ✓ S4 | BrightnessEngine.calculateAnimation (S5) | ported |
| task544 L16062 · Evaluate Light Change | ✓ S1 | ✓ S4 | BrightnessEngine.dynamicThreshold (S5) | ported |
| task546 L16481 · Set Thresholds | ✓ S1 | ✓ S4 | BrightnessEngine.absoluteThresholds (S5) | ported |
| task548 L16630 · DR Compressed Scale | ✓ S1 | ✓ S4 | BrightnessEngine.compressedDynamicScale (S5) | ported |
| task549 L17138 · _GenerateCircadianGraph | ✓ S1 | | | pending |
| task554 L18132 · Process Sensor Event | ✓ S1 | ✓ S4 | BrightnessEngine.kt ingest (S5) | ported |
| task556 L18359 · _GenerateDimmingCurveGraph | ✓ S1 | | | pending |
| task557 L18959 · _GenerateAlphaGraph | ✓ S1 | | | pending |
| task563 L19677 · _AskPermissionsV7 | ✓ S1 | | | pending |
| task592 L24132 · _CreateDefaultProfiles | ✓ S1 | | | pending |
| task618 L25826+L26096 · Set Initial Brightness (×2) | ✓ S1 | ✓ S4 | InitialBrightness.kt (S5) | ported |
| task620 L26400 · _AdaptiveBrightnessSceneSize | ✓ S1 | | | pending |
| task623 L26926 · _ContextManager | ✓ S1 | | app/settings/ContextRuleStore.kt (S10 — upsert/delete CRUD) | ported |
| task625 L27185 · _AppPicker | ✓ S1 | | | pending |
| task626 L27355 · _ContextResume | ✓ S1 | | mergeProfile() 39-key snapshot (S10); RESUME caller (cooldown 0/forced eval) | ported (snapshot+caller) |
| task630 L27585+L27817 · _ContextLocnListener (×2) | ✓ S1 | | | pending |
| task631 L27939+L28432 · _ContextF5NetLoc (×2) | ✓ S1 | | | pending |
| task633 L28827 · _GetWifiForContext | ✓ S1 | | | pending |
| task636 L28993 · _DeleteOverridePoint | ✓ S1 | | | pending |
| task637 L29303 · _ProfileManager | ✓ S1 | | | pending |
| task643 L30505 · _LearnWriteSecure | ✓ S1 | | | pending |
| task655 L32591 · _SetSuggestedVariables | ✓ S1 | ✓ S6 (delegate) | CurveSuggestionEngine.applyToLiveCurve (S6) | ported |
| task657 L32986 · _GenerateCompressionGraph | ✓ S1 | | | pending |
| task663 L33944+L34370 · _GenerateGraph (×2) | ✓ S1 | ✓ S4 (cross-validation oracle, D-002) | chart render = S13 BrightnessCurveChart | reference (chart S13) |
| task696 L35733 · Smooth Brightness Transition | ✓ S1 | ✓ S4 | runtime/AnimationRunner (S9a) | ported |
| task698 L36043 · Smooth DC-Like Transition | ✓ S1 | ✓ S4 | runtime/AnimationRunner brightness path (S9a); S9b wires the ELEVATED secure-dimming layer (task646/650/645 via SuperDimmingCoordinator); DC-like unprivileged overlay deferred S12 (D-040) | ported (brightness + secure dimming; overlay S12) |
| task703 L36847 · _GenerateReactivityGraph | ✓ S1 | | | pending |
| task705 L37517 · _GenerateCircadianDimmingGraph | ✓ S1 | | | pending |
| task90 L40429+L41085 · Dynamic Scale V13 (×2) | ✓ S1 | ✓ S6 (delegate) | SolarCalculator.kt + DynamicScaleEngine.kt (S6) | ported |
