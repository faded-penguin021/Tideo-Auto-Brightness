# PARITY_CHECKLIST — every Tasker artifact, tracked to disposition

Statuses: `pending` → `ported` (with target) / `dropped(reason)` → `device-verified` (human, Gates).
Segments flip rows they own; S14 enforces zero `pending`; the human flips to `device-verified`.
XML anchors: profiles/scenes verified line numbers (see XML_RECIPES.md R4/R5/R7); task anchors
filled by S1/S2 during extraction.

## Profiles (18)

> S1 extracted profiles 753–761, 769 → `extraction/profiles.md` (context+gate+enter/exit+pri). Profiles 762–768, 8 are S2. Status stays `pending` until ported to Kotlin (S9+).

| Profile | XML | Ported to | Status |
|---|---|---|---|
| prof753 Hibernate (Display Off) → task585 | L3 | | pending |
| prof754 Throttle Reinitialization → task566 | L16 | | pending |
| prof755 Allow Override → task567 | L56 | | pending |
| prof756 Repost Paused Notification → task567 | L111 | | pending |
| prof757 Repost Foreground Notification → task584 | L156 | | pending |
| prof758 Dynamic Scale Engine → task90 | L195 | | pending |
| prof759 Proximity Detection → task545 | L300 | | pending |
| prof760 Monitor Ambient Light → task554 (incl. ConditionList gate) | L318 | | pending |
| prof761 Initialize (Display On) → task618 | L386 | | pending |
| prof762 Context: App Changed → task43 | L398 | extraction/contexts_spec.md (S2) | pending |
| prof763 Context: Battery Changed → task43 | L456 | extraction/contexts_spec.md (S2) | pending |
| prof764 Context: Time Changed → task43 | L500 | extraction/contexts_spec.md (S2) | pending |
| prof765 Context: Location Listener → task630 | L541 | extraction/contexts_spec.md (S2) | pending |
| prof766 Context: Location Refresher → task631 | L579 | extraction/contexts_spec.md (S2) | pending |
| prof767 Context: Location Changed → task43 | L628 | extraction/contexts_spec.md (S2) | pending |
| prof768 Context: WiFi (Dis)connected → task43 | L676 | extraction/contexts_spec.md (S2) | pending |
| prof769 Panic (Reset) → task528 | L722 | | pending |
| prof8 Context: Reset Serialized Cache → task26 | L744 | extraction/contexts_spec.md (S2) | pending |

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
| Brightness transitions: 696, 698 | | TaskerReference + transition.csv/dimming.csv (S4) | reference (S9a ports) |
| Software/super dimming math: 700, 645, 646, 647 | | SoftwareDimming.kt (S5 math, golden-tested superdimming.csv 2016 rows, CorePipelineParityTest); 650/653/654/644 privilege writes platform (S9b) | ported (math) |
| Initial brightness on wake: 618 | | TaskerReference (S4) + InitialBrightness.kt (S5) + InitialBrightnessTest.kt (S5) | ported |
| Hibernate/reset: 585 | | | pending |
| Throttle reset: 566 | | | pending |
| Manual override detect/resume: 567, 569, 561, 640, 641, 636 | | OverrideRules.kt (S5 pure logic, unit-tested OverrideRulesTest.kt S5); platform wiring + notification S9a | ported (logic) |
| Panic reset: 528 | | | pending |
| Init/defaults: 570 Initialize AAB Defaults | | | pending |
| Circadian dynamic scale: 90 (+ polar handling) | | SolarCalculator.kt + DynamicScaleEngine.kt (S6 domain, golden-tested circadian.csv 576 rows, CircadianParityTest + 4 polar assertions); BrightnessEngine delegates computeDynamicScale (D-031) | ported |
| Context evaluation: 43, 623, 624, 625, 626, 628, 630, 631, 633, 105, 26 | | S2 extracted → contexts_spec.md | pending |
| Privilege detection/grant: 378, 643, 563 | | S2 extracted → features_spec.md; platform layer: AndroidPrivilegeManager + ShizukuGrantGateway stub (S7); UI wiring S11 | platform-ported (S7) |
| QS tile: 551, 552 | | S2 extracted → features_spec.md | pending |
| Foreground notification: 584, 692 | | S2 extracted → features_spec.md | pending |
| Curve suggestion wizard: 38, 655 | | CurveSuggestionEngine.kt (S6 domain, golden-tested wizard.csv 12 rows, WizardParityTest); applyToLiveCurve = task655 | ported |
| Formula validation: 583, 707 | | S2 extracted → features_spec.md; SettingsValidator.kt (S8 — 5 rules: form2A<0, form3A<0, form2C>zone1End advisory + predicted-brightness@1000lux<25 safety) | ported |
| Power draw calibration: 524 | | S2 extracted → features_spec.md | pending |
| Profiles/import/export/defaults: 592, 637, 622 | | S2 extracted → features_spec.md; DefaultProfiles.kt (S8 — 5 built-in profiles from task592); AabSettings v2 + migration + TaskerLegacyProfileSerializer updated (S8); ContextOverrideRules.kt storage model (S8); wiring to disk S10/S12 | ported (schema+defaults; disk wiring S10/S12) |
| Debug tooling: 634, 635 | | S2 extracted → features_spec.md | pending |
| Misc UI plumbing tasks (scene-resize 620, exits 656, toggles 547/553/555/558/560/576/587/589/638/648/649, chartjs cache 581, logo 619, color 639/379/579/652, about/license/guide 380/401/512, updates 706, experiments 540/541/542/381/382) | | S2 extracted → screen_map.md (scene dispositions) | pending |
| **Anonymous scene-handler tasks (168 unnamed**, incl. 34 `keyTask` back-key handlers) | various | S3.5 census → extraction/tasks/anonymous_handlers.md | pending (S12/S13: every row ported or dropped(reason)) |

## Scenes (20) → M3 screens (~9, per S2 screen_map.md)

> S2 extracted all 20 scenes → `extraction/scenes/*.md` (450-element tables) + `screen_map.md` (full disposition matrix). 'Ported to' = target M3 screen; Status `pending` until rendered (S11–S13).

| Scene | XML | Ported to | Status |
|---|---|---|---|
| AAB Menu | L4462 | Dashboard (M3 nav) | pending (S2 extracted) |
| AAB Brightness Settings | L1415 | Curve & Brightness | pending (S2 extracted) |
| AAB Reactivity Settings | L6739 | Reactivity | pending (S2 extracted) |
| AAB Superdimming Settings | L7533 | Animation & Dimming | pending (S2 extracted) |
| AAB Misc Settings | L4718 | Dashboard/Tools | pending (S2 extracted) |
| AAB Experiment Settings | L3334 | Dynamic Scale | pending (S2 extracted) |
| AAB Profile | L5724 | Profiles & Import/Export | pending (S2 extracted) |
| AAB Debug Scene | L2583 | Tools | pending (S2 extracted) |
| AAB Color Filter | L2552 | Animation & Dimming | pending (S2 extracted) |
| AAB Brightness Graph | L1202 | Curve & Brightness (BrightnessCurveChart) | pending (S2 extracted) |
| AAB Alpha Graph | L1038 | Reactivity (alpha overlay) | pending (S2 extracted) |
| AAB Reactivity Graph | L6563 | Reactivity (ReactivityChart) | pending (S2 extracted) |
| AAB Dimming Graph | L3006 | Animation & Dimming (DimmingChart) | pending (S2 extracted) |
| AAB Circadian Dimming Graph | L2388 | Animation & Dimming (CircadianChart; re-homed S3.5/D-026, visible only when %AAB_ScalingUse on) | pending (S2 extracted) |
| AAB Taper Graph | L8387 | Dynamic Scale (TaperChart) | pending (S2 extracted) |
| AAB Power Draw Graph | L5611 | Tools (PowerDrawChart) | pending (S2 extracted) |
| AAB Experiment Graph | L3170 | Dynamic Scale (ExperimentChart) | pending (S2 extracted) |
| AAB About | L799 | About+Guide+Onboarding | pending (S2 extracted) |
| AAB User Guide | L8551 | About+Guide+Onboarding | pending (S2 extracted) |
| AAB Chart.Js License | L2194 | dropped(Chart.js removed) | pending (S2 extracted) |

## Java blocks (40, anchors verified — see XML_RECIPES.md R7 for the full line↔task table)

| Block (task · line) | Extracted (S1/S2) | Reference impl (S4/S6) | Production port | Status |
|---|---|---|---|---|
| task105 L8906 · _GetWifiNoLocation | ✓ S1 | | | pending |
| task378 L9468 · _PrivilegeDetection | ✓ S1 | | | pending |
| task38 L9921 · _SuggestCurveParameters | ✓ S1 | ✓ S6 (delegate) | CurveSuggestionEngine.kt (S6) | ported |
| task43 L12091 · _EvaluateContexts | ✓ S1 | | | pending |
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
| task623 L26926 · _ContextManager | ✓ S1 | | | pending |
| task625 L27185 · _AppPicker | ✓ S1 | | | pending |
| task626 L27355 · _ContextResume | ✓ S1 | | | pending |
| task630 L27585+L27817 · _ContextLocnListener (×2) | ✓ S1 | | | pending |
| task631 L27939+L28432 · _ContextF5NetLoc (×2) | ✓ S1 | | | pending |
| task633 L28827 · _GetWifiForContext | ✓ S1 | | | pending |
| task636 L28993 · _DeleteOverridePoint | ✓ S1 | | | pending |
| task637 L29303 · _ProfileManager | ✓ S1 | | | pending |
| task643 L30505 · _LearnWriteSecure | ✓ S1 | | | pending |
| task655 L32591 · _SetSuggestedVariables | ✓ S1 | ✓ S6 (delegate) | CurveSuggestionEngine.applyToLiveCurve (S6) | ported |
| task657 L32986 · _GenerateCompressionGraph | ✓ S1 | | | pending |
| task663 L33944+L34370 · _GenerateGraph (×2) | ✓ S1 | ✓ S4 (cross-validation oracle, D-002) | chart render = S13 BrightnessCurveChart | reference (chart S13) |
| task696 L35733 · Smooth Brightness Transition | ✓ S1 | ✓ S4 | runtime write loop = S9a AnimationRunner | reference (S9a ports) |
| task698 L36043 · Smooth DC-Like Transition | ✓ S1 | ✓ S4 | runtime write loop = S9a AnimationRunner | reference (S9a ports) |
| task703 L36847 · _GenerateReactivityGraph | ✓ S1 | | | pending |
| task705 L37517 · _GenerateCircadianDimmingGraph | ✓ S1 | | | pending |
| task90 L40429+L41085 · Dynamic Scale V13 (×2) | ✓ S1 | ✓ S6 (delegate) | SolarCalculator.kt + DynamicScaleEngine.kt (S6) | ported |
