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
| prof755 Allow Override → task567 | L56 | runtime/OverrideMonitor + ProfileGates.allowOverrideGate (S9a); S12.7a adds post-init settle-suppression (F64) + corrected override/context-rule semantics (F46, D-054) | ported |
| prof756 Repost Paused Notification → task567 | L111 | runtime/AmbientMonitoringService paused notification (S9a) | ported |
| prof757 Repost Foreground Notification → task584 | L156 | runtime/AmbientMonitoringService live notification (S9a) | ported |
| prof758 Dynamic Scale Engine → task90 | L195 | gate transcribed: ProfileGates.dynamicScaleGate + truth-table test (S9a); task90 scheduling S9b/S12 | partial (gate S9a) |
| prof759 Proximity Detection → task545 | L300 | | pending |
| prof760 Monitor Ambient Light → task554 (incl. ConditionList gate) | L318 | ProfileGates.monitorAmbientLightGate + truth-table test + BrightnessPipelineController (S9a) | ported |
| prof761 Initialize (Display On) → task618 | L386 | runtime/BrightnessPipelineController.reinit/setInitialBrightness (S9a) | ported |
| prof762 Context: App Changed → task43 | L398 | runtime/ContextEngine (S10 — foreground-app poll → APP_CHANGED veto → ContextOverrideResolver); AndroidContextSignalSource | ported (engine; rule UI S12) |
| prof763 Context: Battery Changed → task43 | L456 | runtime/ContextEngine (S10 — BatteryStateReader flow → BATTERY veto Δ≥5%/plug-flip → resolver) | ported (engine; rule UI S12) |
| prof764 Context: Time Changed → task43 | L500 | runtime/ContextEngine (S10 — time-window eval on pipeline ticks; nextContextTime computed by resolver) | ported (engine; rule UI S12) |
| prof765 Context: Location Listener → task630 | L541 | S12.7c: `LocationReader.locationUpdates()` continuous `requestLocationUpdates` (NETWORK+GPS, last-known seed, (0,0) filter) hosted in the FGS scope, gated on the `[LOC]` ContextCache token, ≥100 m debounce (G2R-F45, D-056) — supersedes the old passive last-known read (D-042) | ported (continuous listener; gated + debounced) |
| prof766 Context: Location Refresher → task631 | L579 | S12.7c: superseded by the persistent `locationUpdates` listener (D-056); the task631 adaptive-backoff/zombie watchdog is folded into the OS `requestLocationUpdates` lifecycle | ported (subsumed by F45 listener) |
| prof767 Context: Location Changed → task43 | L628 | runtime/ContextEngine (S10 LOCATION caller; S12.7c feeds it real continuous fixes via `locationFlow()` + a ≥100 m debounce, D-056) | ported (engine + live fixes) |
| prof768 Context: WiFi (Dis)connected → task43 | L676 | runtime/ContextEngine (S10 — WifiInfoReader SSID flow → WIFI veto on SSID change → resolver) | ported (engine; rule UI S12; S12.6e: `currentSsid()` suspend NetworkCallback w/ FLAG_INCLUDE_LOCATION_INFO fixes API-29+ SSID redaction, G2R-F22) |
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
| Manual override detect/resume: 567, 569, 561, 640, 641, 636 | | OverrideRules.kt (S5 pure logic, OverrideRulesTest.kt S5); runtime wiring: OverrideMonitor + controller pause/resume + recordOverridePoint (S9a); S12.6c added the task567 act8 settle-wait re-read (no rapid-light false-pause, G2R-F26/D-049) + device-exact AnimationRunner read-back, and **captures+persists** the points to `OverridePointStore` (G2R-F13, closes D-044c). 640/641/636 override-array CRUD UI = S12.6d | ported (detect/pause/resume + capture/persist) |
| Panic reset: 528 | | runtime/BrightnessPipelineController.emergencyStop + notification Reset action; Gate-1 G1-F4 fix: panic is a FULL STOP (restore 255 + drop dimming + %AAB_Service=Off + service teardown), not a pausable state (D-041) | ported |
| Init/defaults: 570 Initialize AAB Defaults | | | pending |
| Circadian dynamic scale: 90 (+ polar handling) | | SolarCalculator.kt + DynamicScaleEngine.kt (S6 domain, golden-tested circadian.csv 576 rows, CircadianParityTest + 4 polar assertions); BrightnessEngine delegates computeDynamicScale (D-031) | ported |
| Context evaluation: 43, 623, 624, 625, 626, 628, 630, 631, 633, 105, 26 | | S10: ContextOverrideResolver (task43 PASS3/4, 21-case matrix test) + ContextEngine (PASS1 cooldown/PASS2 veto) + ContextRuleStore (task623 upsert/delete CRUD); S12.7c lands the 630/631 location-listener subsystem (continuous gated/debounced `locationUpdates`, D-056), priority-ordered rule list (F43), legacy profiles as rule targets (F44), day-of-week picker (F67) + sunrise/sunset resolved-time tokens (F68) | ported (eval + CRUD + location listener + rule-UI fidelity) |
| Privilege detection/grant: 378, 643, 563 | | S2 extracted → features_spec.md; platform layer: AndroidPrivilegeManager + ShizukuGrantGateway (S7); S11 UI: OnboardingScreen stepper (POST_NOTIFICATIONS → WRITE_SETTINGS re-check → optional ELEVATED with adb-copy/Shizuku/root + usage-access) and Dashboard tier badge; Shizuku user-service `pm grant` exec completed (AIDL IShizukuUserService, closes D-032) | ported (detect + grant UI + Shizuku exec) |
| QS tile: 551, 552 | | S2 extracted → features_spec.md; runtime/BrightnessTileService (S9b — toggles serviceEnabled + start/stop FGS via AutoBrightnessRuntime; manifest QS_TILE entry + BIND_QUICK_SETTINGS_TILE); S12.5c — tile subtitle reflects Off/Active/Paused/Starting from LiveRuntimeState (G2-F17); BrightnessTileServiceTest instantiation smoke | ported (toggle + paused state) |
| Foreground notification: 584, 692 | | S2 extracted → features_spec.md; runtime/AmbientMonitoringService live lux/target notification + Pause/Resume/Reset/Disable actions (S9a) | ported |
| Curve suggestion wizard: 38, 655 | | CurveSuggestionEngine.kt (S6 domain, golden-tested wizard.csv 12 rows, WizardParityTest); applyToLiveCurve = task655 | ported |
| Formula validation: 583, 707 | | S2 extracted → features_spec.md; SettingsValidator.kt (S8 — 5 rules: form2A<0, form3A<0, form2C>zone1End + predicted-brightness@1000lux<25 safety). S12.6d: the 3 form-coefficient rules now carry `Severity.CRITICAL` and **block Apply** (G2R-F18/D-052 sanctioned deviation); safety/range stay advisory | ported |
| Power draw calibration: 524 | | S2 extracted → features_spec.md; Tools entry + PowerDrawChart slot (S12); on-device current-sampling measurement deferred (D-044) | partial (UI entry S12; measurement deferred) |
| Profiles/import/export/defaults: 592, 637, 622 | | DefaultProfiles.kt (S8); AabSettings v2 + migration + TaskerLegacyProfileSerializer (S8); ContextRuleStore (S10); Profiles screen (S12 — apply/reset + CreateDocument/OpenDocument import-export incl. legacy). **S12.6d**: user-editable saved profiles (UserProfileStore: built-ins seeded once + Save-current-as + overwrite + delete + **Restore factory profiles**, G2R-F15); AppProfileCatalog reads the store so context rules target user profiles (closes D-042c); **SAF folder grant** (OpenDocumentTree→Download/AAB/configs, list+load *.json via LegacyConfigImporter, G2R-F16); per-screen reset (G2R-F17); manual-load context lock + Resume (G2R-F30) | ported |
| Debug tooling: 634, 635 | | S12.6b: dedicated **Live Debug Info** scene (AppRoute.LiveDebug, in Menu) = glass-box %AAB_* readout grouped per debug.md (Core Metrics / Circadian & Scale / System Status / Performance), gold values, G2R-F6; the 10-label debug selector is now GLOBAL there (moved off Misc, preserved across profile/reset/draft applies, G2R-F9) + per-screen DiagnosticCards on Reactivity/Circadian (G2R-F7/F8); teal custom-view debug toasts (G2R-F10). Runtime debug toasts for all 10 %AAB_Debug categories via DebugSink/ToastDebugSink (S12.5c, G2-F15: LIGHT_EVAL/ANIMATION/DYNAMIC_SCALE/GRAPH_METRICS/SKIP_ANIMATIONS in the pipeline, SUPER_DIMMING in the coordinator, CONTEXT_AUTOMATION/LOCATION in ContextEngine); **S12.7e**: flashes routed through a process-wide `AabFlash` channel — cancel-previous not stack (G2R-F51), instant debug-off via cancel+reapply (G2R-F52), opt-in system-wide flashes via `AabToastAccessibilityService` overlay (G2R-F50), `DynamicScaleDebugGate` 2-min transition timing (G2R-F48), `OVERLAY_PREVIEW` colour flash on the unprivileged super-dimming fallback (G2R-F49); persistent in-app log view deferred (D-044) | ported (Live Debug scene + global selector + diagnostic cards + runtime toasts + global/cancel/instant-off + overlay-preview; log view deferred) |
| Misc UI plumbing tasks (scene-resize 620, exits 656, toggles 547/553/555/558/560/576/587/589/638/648/649, chartjs cache 581, logo 619, color 639/379/579/652, about/license/guide 380/401/512, updates 706, experiments 540/541/542/381/382) | | S2 extracted → screen_map.md (scene dispositions) | pending |
| **Anonymous scene-handler tasks (168 unnamed**, incl. 34 `keyTask` back-key handlers) | various | S3.5 census + S12 Step-0 triage (a/b/c buckets) → extraction/tasks/anonymous_handlers.md | ported (S12 — (a)/(b) dropped w/ shared reasons, (c) ported into 7 screens; chart-gen rows deferred-S13) |

## Scenes (20) → M3 screens (~9, per S2 screen_map.md)

> S2 extracted all 20 scenes → `extraction/scenes/*.md` (450-element tables) + `screen_map.md` (full disposition matrix). 'Ported to' = target M3 screen; Status `pending` until rendered (S11–S13).

| Scene | XML | Ported to | Status |
|---|---|---|---|
| AAB Menu | L4462 | Menu (home hub) | ported (S11 nav shell → S12.6a promoted to a real `Menu` home screen: banner + Profiles/Contexts hero cards + grouped nav; start dest + back-target for all screens) |
| AAB Brightness Settings | L1415 | Curve & Brightness | ported (S12.5b — curve-zone coefficients + live form2A/3A + draft→Apply preview chart; min/max/offset/scale moved to Misc, G2-F2; **S12.6e**: Tasker scene labels + verbatim long-press help via ⓘ reveal, G2R-F19/F21; **S12.7f**: `current_lux_and_bright` live-readout card, G2R-F58; form2A/3A relabelled "Zone 2/3 alignment", G2R-F61) |
| AAB Reactivity Settings | L6739 | Reactivity | ported (S12.5b — thresholds + DetectOverrides/trust; draft→Apply; chart slot S13; **S12.6e**: Tasker labels + verbatim help; "delta factor"→"Smoothing Δ" was a label bug not a wiring bug, G2R-F19/F20; **S12.7f**: live reactivity threshold shown as % (G2R-F56) + dynamic-threshold description substitutes live %AAB_ThreshDynamic behind ⓘ (G2R-F59)) |
| AAB Superdimming Settings | L7533 | Super Dimming (renamed S12.6a/G2R-F3) | ported (S12.5b — ELEVATED-gated super dimming + PWM, mutually-exclusive (G2-F10), dim-spread gated on circadian (G2-F11); anim moved to Misc; chart slot S13; **S12.6e**: Tasker labels + verbatim help, G2R-F19/F21) |
| AAB Misc Settings | L4718 | Misc | ported (S12.5b — dedicated Misc screen, G2-F2: min/max sliders 0–75/150–255, offset/scale text, anim sliders + derived throttle, notifications + debug selector; **S12.6e**: Tasker labels + verbatim help, G2R-F19/F21; **S12.7f**: `current_throttle_and_alpha` live-readout card, G2R-F58; "Scale" → read-only "(auto)" dynamic-scale when circadian on, G2R-F60) |
| AAB Experiment Settings | L3334 | Circadian (renamed S12.6a/G2R-F4) | ported (S12.5b — scaling/taper + taper-midpoint slider 130–240 (G2-F13) + warnings; chart slot S13) |
| AAB Profile | L5724 | Profiles & Import/Export | ported (S12 — built-in profiles + reset + JSON/legacy import-export + context CRUD; reapply-on-load S12.5b; context-rule editor fidelity S12.5c — `<queries>` app list w/ icons, use-current-SSID, SUNRISE/SUNSET tokens, usage-access prompt, save/delete toasts; profile-load keeps DetectOverrides G2-F8. **S12.6d**: user-editable saved profiles + overwrite + factory restore, SAF folder legacy import, per-screen reset, manual-load context lock + Resume; **battery % from/to** added to the rule editor, G2R-F31. **S12.6e**: Wi-Fi SSID via suspend NetworkCallback→`SsidResult` w/ targeted messages, live location lat/lon/radius + "use current location", M3 TimePicker modal (G2R-F22/F28), runtime context-load toast (G2R-F25)) |
| AAB Debug Scene | L2583 | LiveDebug | ported (S12.6b — rebuilt as the dedicated **Live Debug Info** scene: glass-box %AAB_* readout grouped per the debug.md HTML cards + the now-global 10-label debug selector, G2R-F6/F9; S12.5c selector drives runtime debug toasts G2-F15; %AAB_Test wizard report→clipboard stays on Tools, which keeps wizard + calibration entry; **S12.6e**: Performance & Timings card reaches Tasker parity — luxAlpha/cycle/throttle/last-animation(steps×wait)/last-update, G2R-F29) |
| AAB Color Filter | L2552 | Super Dimming | ported (S12 — PWM-sensitive + exponent rows; **S12.7e** surfaces the computed overlay hex via the `OVERLAY_PREVIEW` debug flash on the unprivileged fallback, G2R-F49; the actual overlay window still deferred, D-040) |
| AAB Brightness Graph | L1202 | Curve & Brightness (BrightnessCurveChart) | ported (S12 — BrightnessCurveChart = chart template; ChartCanvas engine; **S12.7g**: log-x from 0.1 + nice y-ticks + axis labels (F55), interactive scrub readout (F55), Reference/Curve legend (F66), FIXED dashed-gold committed reference (F69), wizard fit on the curve view ≥9 pts (F62), tap-to-delete override points (F36)) |
| AAB Alpha Graph | L1038 | Reactivity (alpha overlay) | partial (S12 host slot; chart render S13) |
| AAB Reactivity Graph | L6563 | Reactivity (ReactivityChart) | partial (S12 host slot; chart render S13) |
| AAB Dimming Graph | L3006 | Super Dimming (DimmingChart) | partial (S12 host slot; chart render S13) |
| AAB Circadian Dimming Graph | L2388 | Super Dimming (CircadianChart; re-homed S3.5/D-026, visible only when %AAB_ScalingUse on) | partial (S12 host slot; chart render S13) |
| AAB Taper Graph | L8387 | Circadian (TaperChart) | partial (S12 host slot; chart render S13) |
| AAB Power Draw Graph | L5611 | Tools (PowerDrawChart) | partial (S12 host slot; chart + on-device calibration S13/Gate) |
| AAB Experiment Graph | L3170 | Circadian (ExperimentChart) | partial (S12 host slot; chart render S13) |
| AAB About | L799 | About+Guide+Onboarding | partial (S11 — onboarding/privilege stepper done; About content S13) |
| AAB User Guide | L8551 | About+Guide+Onboarding | partial (S11 — onboarding done; User Guide content S13) |
| AAB Chart.Js License | L2194 | dropped(Chart.js removed) | pending (S2 extracted) |

## Java blocks (40, anchors verified — see XML_RECIPES.md R7 for the full line↔task table)

| Block (task · line) | Extracted (S1/S2) | Reference impl (S4/S6) | Production port | Status |
|---|---|---|---|---|
| task105 L8906 · _GetWifiNoLocation | ✓ S1 | | platform/WifiInfoReader: real _GetWifiNoLocation V3 order (S12.7d, G2R-F41) — Shizuku `cmd wifi status` (ShizukuShell.exec) → in-process `dumpsys wifi` → Location NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) last (WifiSsidStrategies.kt) | ported (no-Location SSID order) |
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
| task563 L19677 · _AskPermissionsV7 | ✓ S1 | | app/ui/onboarding/OnboardingScreen.kt — notifications → WRITE_SETTINGS → Location → ELEVATED → usage; S12.7d adds restricted-settings hint (F33) + Location step (F41) + Menu landing (F57) | ported (onboarding gates) |
| task592 L24132 · _CreateDefaultProfiles | ✓ S1 | | | pending |
| task618 L25826+L26096 · Set Initial Brightness (×2) | ✓ S1 | ✓ S4 | InitialBrightness.kt (S5) | ported |
| task620 L26400 · _AdaptiveBrightnessSceneSize | ✓ S1 | | | pending |
| task623 L26926 · _ContextManager | ✓ S1 | | app/settings/ContextRuleStore.kt (S10 — upsert/delete CRUD) | ported |
| task625 L27185 · _AppPicker | ✓ S1 | | | pending |
| task626 L27355 · _ContextResume | ✓ S1 | | mergeProfile() 39-key snapshot (S10); RESUME caller (cooldown 0/forced eval) | ported (snapshot+caller) |
| task630 L27585+L27817 · _ContextLocnListener (×2) | ✓ S1 | | | pending |
| task631 L27939+L28432 · _ContextF5NetLoc (×2) | ✓ S1 | | | pending |
| task633 L28827 · _GetWifiForContext | ✓ S1 | | platform/WifiInfoReader.currentSsid (S12.7d) — typed SsidResult feeding the rule editor's "use current SSID" via the no-Location order | ported (editor SSID read) |
| task636 L28993 · _DeleteOverridePoint | ✓ S1 | | | pending |
| task637 L29303 · _ProfileManager | ✓ S1 | | | pending |
| task643 L30505 · _LearnWriteSecure | ✓ S1 | | | pending |
| task655 L32591 · _SetSuggestedVariables | ✓ S1 | ✓ S6 (delegate) | CurveSuggestionEngine.applyToLiveCurve (S6) | ported |
| task657 L32986 · _GenerateCompressionGraph | ✓ S1 | | | pending |
| task663 L33944+L34370 · _GenerateGraph (×2) | ✓ S1 | ✓ S4 (cross-validation oracle, D-002) | chart render = S13 BrightnessCurveChart | reference (chart S13) |
| task696 L35733 · Smooth Brightness Transition | ✓ S1 | ✓ S4 | runtime/AnimationRunner (S9a); S12.7a ports the REAL band+2-read-debounce override detector (java L49-56/L121-137) replacing the exact-match self-write check (F34, D-054) | ported |
| task698 L36043 · Smooth DC-Like Transition | ✓ S1 | ✓ S4 | runtime/AnimationRunner brightness path (S9a); S9b wires the ELEVATED secure-dimming layer (task646/650/645 via SuperDimmingCoordinator); S12.6c adds the **PWM-sensitive hardware floor** (step 3: clamp hardware up to dimmingThreshold, G2R-F27/D-050); DC-like unprivileged overlay still deferred (D-040) | ported (brightness + secure dimming + PWM hardware floor; overlay deferred) |
| task703 L36847 · _GenerateReactivityGraph | ✓ S1 | | | pending |
| task705 L37517 · _GenerateCircadianDimmingGraph | ✓ S1 | | | pending |
| task90 L40429+L41085 · Dynamic Scale V13 (×2) | ✓ S1 | ✓ S6 (delegate) | SolarCalculator.kt + DynamicScaleEngine.kt (S6) | ported |
