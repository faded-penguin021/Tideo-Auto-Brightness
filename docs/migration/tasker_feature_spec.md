# Tasker Feature Migration Spec

## 1) Profile trigger map

- **753 / Hibernate (Display Off)** → entry task `Reset Brightness and State` (`mid0=585`)
  - Event code `210` ⇒ **Display Off event**
- **754 / Throttle Reinitialization** → entry task `Reset Throttle` (`mid0=566`)
  - State code `123` ⇒ **Variable value state**
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_Throttle (op 3) %AAB_DefaultThrottle`
    - condition: `%LastAAB (op 12) None`
  - Event code `2095` ⇒ **Tick/delayed timer event**
- **755 / Allow Override** → entry task `Manual Override` (`mid0=567`)
  - Event code `2075` ⇒ **System setting changed event**
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_Service (op 2) On`
    - condition: `%AutoBrightRunning (op 2) 0`
    - condition: `%AAB_Manual_Override (op 1) true`
    - condition: `%AAB_Initializing (op 1) true`
    - condition: `%AAB_DetectOverrides (op 1) Off`
  - State code `123` ⇒ **Variable value state**
- **756 / Repost Paused Notification** → entry task `Manual Override` (`mid0=567`)
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_Service (op 1) On`
    - condition: `%AAB_ResumeTapped (op 1) On`
    - condition: `%SCREEN (op 2) On`
    - condition: `%AAB_Manual_Override (op 2) true`
- **757 / Repost Foreground Notification** → entry task `_ForegroundNotification` (`mid0=584`)
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%SCREEN (op 2) On`
    - condition: `%AAB_Service (op 2) On`
    - condition: `%AAB_Manual_Override (op 1) true`
- **758 / Dynamic Scale Engine** → entry task `Dynamic Scale V13 (Java) App Version` (`mid0=90`)
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_MorningStart (op 6) %TIMES % 86400`
    - condition: `%AAB_MorningEnd (op 7) %TIMES % 86400`
    - condition: `%AAB_EveningStart (op 6) %TIMES % 86400 - 86400`
    - condition: `%AAB_EveningEnd (op 7) %TIMES % 86400 - 86400`
    - condition: `%AAB_SunLastDate (op 3) %DATE`
    - condition: `%AAB_ScalingUse (op 2) true`
    - condition: `%AAB_MorningStart (op 6) %TIMES % 86400 + 86400`
    - condition: `%AAB_MorningEnd (op 7) %TIMES % 86400 + 86400`
    - condition: `%AAB_MorningStart (op 6) %TIMES % 86400 - 86400`
    - condition: `%AAB_MorningEnd (op 7) %TIMES % 86400 - 86400`
    - condition: `%AAB_EveningStart (op 6) %TIMES % 86400`
    - condition: `%AAB_EveningEnd (op 7) %TIMES % 86400`
    - condition: `%AAB_EveningStart (op 6) %TIMES % 86400 + 86400`
    - condition: `%AAB_EveningEnd (op 7) %TIMES % 86400 + 86400`
- **759 / Proximity Detection** → entry task `Detect Proximity` (`mid0=545`)
  - State code `125` ⇒ **Proximity sensor state**
  - State code `123` ⇒ **Variable value state**
- **760 / Monitor Ambient Light** → entry task `Process Sensor Event (Java)` (`mid0=554`)
  - Event code `2088` ⇒ **Periodic timer event**
    - condition: `%AAB_TrustUnreliable (op 2) On`
    - condition: `%AAB_TrustUnreliable (op 2) Off`
    - condition: `%as_accuracy (op 7) 1`
    - condition: `%as_values1 (op 6) %AAB_ThreshAbsLow`
    - condition: `%as_values1 (op 7) %AAB_ThreshAbsHigh`
    - condition: `%AAB_MainLoop (op 3) On`
- **761 / Initialize (Display On)** → entry task `Set Initial Brightness (Java) V3` (`mid0=618`)
  - Event code `208` ⇒ **Display On event**
- **762 / Context: App Changed** → entry task `_EvaluateContexts V2` (`mid0=43`)
  - Event code `2078` ⇒ **App changed event**
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_Manual_Override (op 3) true`
    - condition: `%AAB_Service (op 2) On`
    - condition: `%AAB_ContextOverride (op 3) true`
    - condition: `%AAB_ContextCache (op 2) *.*`
  - State code `123` ⇒ **Variable value state**
- **763 / Context: Battery Changed** → entry task `_EvaluateContexts V2` (`mid0=43`)
  - Event code `203` ⇒ **Battery Changed event**
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_Manual_Override (op 3) true`
    - condition: `%AAB_Service (op 2) On`
    - condition: `%AAB_ContextCache (op 2) *[BATT]*`
    - condition: `%AAB_ContextOverride (op 3) true`
  - State code `123` ⇒ **Variable value state**
- **764 / Context: Time Changed** → entry task `_EvaluateContexts V2` (`mid0=43`)
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_Manual_Override (op 3) true`
    - condition: `%AAB_Service (op 2) On`
    - condition: `%AAB_NextContextTime (op 12) None`
    - condition: `%AAB_ContextOverride (op 3) true`
- **765 / Context: Location Listener** → entry task `_ContextLocnListener V4` (`mid0=630`)
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_Service (op 2) On`
    - condition: `%AAB_Manual_Override (op 3) true`
    - condition: `%AAB_ContextOverride (op 3) true`
    - condition: `%AAB_ContextCache (op 2) *[LOC]*`
- **766 / Context: Location Refresher** → entry task `_ContextF5NetLoc V8` (`mid0=631`)
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_Manual_Override (op 3) true`
    - condition: `%AAB_Service (op 2) On`
    - condition: `%AAB_ContextCache (op 2) *[LOC]*`
    - condition: `%AAB_ContextOverride (op 3) true`
  - State code `123` ⇒ **Variable value state**
- **767 / Context: Location Changed** → entry task `_EvaluateContexts V2` (`mid0=43`)
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_Manual_Override (op 3) true`
    - condition: `%AAB_Service (op 2) On`
    - condition: `%AAB_ContextCache (op 2) *[LOC]*`
    - condition: `%AAB_ContextOverride (op 3) true`
  - State code `123` ⇒ **Variable value state**
  - Event code `3050` ⇒ **Variable set event**
- **768 / Context: WiFi (Dis)connected** → entry task `_EvaluateContexts V2` (`mid0=43`)
  - State code `160` ⇒ **WiFi connection state**
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_Manual_Override (op 3) true`
    - condition: `%AAB_Service (op 2) On`
    - condition: `%AAB_ContextCache (op 2) *[WIFI]*`
    - condition: `%AAB_ContextOverride (op 3) true`
- **769 / Panic (Reset)** → entry task `_PanicButton` (`mid0=528`)
  - Event code `2083` ⇒ **Device boot event**
  - State code `120` ⇒ **Display state**
  - State code `123` ⇒ **Variable value state**
- **8 / Context: Reset Serialized Cache** → entry task `_ResetContextCacheDaily` (`mid0=26`)
  - State code `165` ⇒ **Expression/variable condition state**
    - condition: `%AAB_ContextCache (op 12) None`

## 2) Task dependency graph

- `_GetWifiNoLocation V3 (105)` --Perform Task--> `_PrivilegeDetection V5 (Java) (378)`
- `unnamed_384 (384)` --Perform Task--> `_SaveButtonGeneral (582)`
- `unnamed_386 (386)` --Perform Task--> `_SaveButtonGeneral (582)`
- `unnamed_391 (391)` --Perform Task--> `_ExitButton (656)`
- `unnamed_396 (396)` --Perform Task--> `Initialize AAB Defaults (570)`
- `unnamed_396 (396)` --Perform Task--> `Advanced Auto Brightness (580)`
- `unnamed_397 (397)` --Perform Task--> `_QSToggleAABService V2 (551)`
- `unnamed_402 (402)` --Perform Task--> `_QSToggleAABService V2 (551)`
- `unnamed_403 (403)` --Perform Task--> `_SaveButtonMisc (564)`
- `unnamed_406 (406)` --Perform Task--> `_SaveButtonReactivity (577)`
- `_SuggestCurveParameters V23 (Hybrid) (41)` --Perform Task--> `Advanced Auto Brightness (580)`
- `unnamed_411 (411)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `unnamed_411 (411)` --Perform Task--> `_LoadedReactivityToggle (560)`
- `unnamed_411 (411)` --Perform Task--> `_LoadedOverrideToggle (641)`
- `unnamed_412 (412)` --Perform Task--> `Advanced Auto Brightness (580)`
- `unnamed_412 (412)` --Perform Task--> `Advanced Auto Brightness (580)`
- `unnamed_417 (417)` --Perform Task--> `_ExitButton (656)`
- `_EvaluateContexts V2 (43)` --Perform Task--> `_ProfileManager (637)`
- `_EvaluateContexts V2 (43)` --Perform Task--> `Set Initial Brightness (Java) V3 (618)`
- `_EvaluateContexts V2 (43)` --Perform Task--> `_GetWifiForContext (633)`
- `_EvaluateContexts V2 (43)` --Perform Task--> `_GetWifiNoLocation V3 (105)`
- `unnamed_462 (462)` --Perform Task--> `_MenuScene (562)`
- `unnamed_466 (466)` --Perform Task--> `_SaveButtonDimming (588)`
- `unnamed_466 (466)` --Perform Task--> `_DimmingApplyToggle (589)`
- `unnamed_473 (473)` --Perform Task--> `_GenerateReactivityGraph (Java) (703)`
- `unnamed_473 (473)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `unnamed_481 (481)` --Perform Task--> `_ExitButton (656)`
- `unnamed_490 (490)` --Perform Task--> `_ExitButton (656)`
- `unnamed_492 (492)` --Perform Task--> `_PrivilegeDetection V5 (Java) (378)`
- `unnamed_500 (500)` --Perform Task--> `Initialize AAB Defaults (570)`
- `unnamed_500 (500)` --Perform Task--> `_SuperDimmingScene (586)`
- `unnamed_509 (509)` --Perform Task--> `_DimmingUIToggle (638)`
- `unnamed_509 (509)` --Perform Task--> `_DimmingApplyToggle (589)`
- `unnamed_511 (511)` --Perform Task--> `_DimmingUIToggle (638)`
- `unnamed_511 (511)` --Perform Task--> `_DimmingApplyToggle (589)`
- `unnamed_513 (513)` --Perform Task--> `_GenerateDimmingCurveGraph (Java) (556)`
- `unnamed_513 (513)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `unnamed_515 (515)` --Perform Task--> `_SaveButtonDimming (588)`
- `unnamed_516 (516)` --Perform Task--> `_LoadedDimmingToggle (587)`
- `unnamed_517 (517)` --Perform Task--> `Dynamic Scale V13 (Java) App Version (90)`
- `unnamed_517 (517)` --Perform Task--> `_GenerateCircadianDimmingGraph (Java) (705)`
- `unnamed_517 (517)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `unnamed_520 (520)` --Perform Task--> `_SaveButtonDimming (588)`
- `unnamed_521 (521)` --Perform Task--> `_LoadedDimmingToggle (587)`
- `_CalibratePowerDraw (524)` --Perform Task--> `_ChartJsChunks (581)`
- `_CalibratePowerDraw (524)` --Perform Task--> `_ExitButton (656)`
- `_CalibratePowerDraw (524)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `_CalibratePowerDraw (524)` --Perform Task--> `_QSToggleAABService V2 (551)`
- `_CalibratePowerDraw (524)` --Perform Task--> `_QSToggleAABService V2 (551)`
- `unnamed_525 (525)` --Perform Task--> `_OverrideToggle (640)`
- `unnamed_526 (526)` --Perform Task--> `_OverrideToggle (640)`
- `unnamed_527 (527)` --Perform Task--> `_SuperDimmingLongTap (644)`
- `_PanicButton (528)` --Perform Task--> `_QSToggleAABService V2 (551)`
- `_PanicButton (528)` --Perform Task--> `Disable Super Dimming (Unprivileged) (654)`
- `_PanicButton (528)` --Perform Task--> `Disable Super Dimming (Privileged) (645)`
- `unnamed_533 (533)` --Perform Task--> `_PWMToggle (648)`
- `unnamed_533 (533)` --Perform Task--> `_LoadedPWMToggle (649)`
- `unnamed_534 (534)` --Perform Task--> `_PWMToggle (648)`
- `unnamed_534 (534)` --Perform Task--> `_LoadedPWMToggle (649)`
- `unnamed_537 (537)` --Perform Task--> `_ExitButton (656)`
- `unnamed_538 (538)` --Perform Task--> `_ExitButton (656)`
- `_ExperimentScene (540)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `_ExperimentScene (540)` --Perform Task--> `_LoadedExperimentTogglesV2 (558)`
- `_SaveButtonExperiment (541)` --Perform Task--> `Dynamic Scale V13 (Java) App Version (90)`
- `Evaluate Light Change (Java) V2 (544)` --Perform Task--> `Map Lux to Brightness (Java) V2 (661)`
- `Evaluate Light Change (Java) V2 (544)` --Perform Task--> `Set Thresholds (Java) (546)`
- `Evaluate Light Change (Java) V2 (544)` --Perform Task--> `Lux Smoothing (Java) (535)`
- `Evaluate Light Change (Java) V2 (544)` --Perform Task--> `Map Lux to Brightness (Java) V2 (661)`
- `Evaluate Light Change (Java) V2 (544)` --Perform Task--> `Set Thresholds (Java) (546)`
- `_GenerateCircadianGraph V8 (Java) (549)` --Perform Task--> `_ChartJsChunks (581)`
- `_QSToggleAABService V2 (551)` --Perform Task--> `_MainSwitchUI V2 (553)`
- `_QSToggleAABService V2 (551)` --Perform Task--> `Dimming Decider (578)`
- `_QSToggleAABService V2 (551)` --Perform Task--> `Reset Brightness and State (585)`
- `_QSToggleAABService V2 (551)` --Perform Task--> `_MainSwitchUI V2 (553)`
- `_QSToggleAABService V2 (551)` --Perform Task--> `Set Initial Brightness (Java) V3 (618)`
- `_QSToggleAABService V2 (551)` --Perform Task--> `_ForegroundNotification (584)`
- `_QSToggleAABService V2 (551)` --Perform Task--> `Dimming Decider (578)`
- `Process Sensor Event (Java) (554)` --Perform Task--> `Evaluate Light Change (Java) V2 (544)`
- `_GenerateDimmingCurveGraph (Java) (556)` --Perform Task--> `_ChartJsChunks (581)`
- `_GenerateAlphaGraph (Java) (557)` --Perform Task--> `_ChartJsChunks (581)`
- `_MenuScene (562)` --Perform Task--> `_ExitButton (656)`
- `_AskPermissionsV7 (563)` --Perform Task--> `Advanced Auto Brightness (580)`
- `_SaveButtonMisc (564)` --Perform Task--> `Set Initial Brightness (Java) V3 (618)`
- `_MiscScene (565)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `_MiscScene (565)` --Perform Task--> `_LoadedMiscAuto (576)`
- `Manual Override (567)` --Perform Task--> `Process Overrides (561)`
- `Manual Override (567)` --Perform Task--> `_QSToggleAABService V2 (551)`
- `Resume After Override (569)` --Perform Task--> `Set Initial Brightness (Java) V3 (618)`
- `Resume After Override (569)` --Perform Task--> `_QSToggleAABService V2 (551)`
- `_ReactivityScene (575)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `_ReactivityScene (575)` --Perform Task--> `_LoadedReactivityToggle (560)`
- `_ReactivityScene (575)` --Perform Task--> `_LoadedOverrideToggle (641)`
- `_SaveButtonReactivity (577)` --Perform Task--> `Set Initial Brightness (Java) V3 (618)`
- `Dimming Decider (578)` --Perform Task--> `Calculate Super Dimming (Unprivileged) V3 (647)`
- `Dimming Decider (578)` --Perform Task--> `_ShowColorFilter (579)`
- `Dimming Decider (578)` --Perform Task--> `Disable Super Dimming (Unprivileged) (654)`
- `Dimming Decider (578)` --Perform Task--> `Calculate Super Dimming (Privileged) V4 (646)`
- `Dimming Decider (578)` --Perform Task--> `Disable Super Dimming (Privileged) (645)`
- `Advanced Auto Brightness (580)` --Perform Task--> `Initialize AAB Defaults (570)`
- `Advanced Auto Brightness (580)` --Perform Task--> `_AskPermissionsV7 (563)`
- `Advanced Auto Brightness (580)` --Perform Task--> `_SetSuggestedVariables (655)`
- `Advanced Auto Brightness (580)` --Perform Task--> `_ColorSuggestionsScene (652)`
- `Advanced Auto Brightness (580)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `Advanced Auto Brightness (580)` --Perform Task--> `_LoadedMainToggle (547)`
- `Advanced Auto Brightness (580)` --Perform Task--> `_Updates (706)`
- `Advanced Auto Brightness (580)` --Perform Task--> `_ExitButton (656)`
- `_SaveButtonGeneral (582)` --Perform Task--> `Set Initial Brightness (Java) V3 (618)`
- `_SaveButtonGeneral (582)` --Perform Task--> `_ValidateBrightnessParams (707)`
- `Reset Brightness and State (585)` --Perform Task--> `Disable Super Dimming (Privileged) (645)`
- `Reset Brightness and State (585)` --Perform Task--> `_ContextResume (626)`
- `_SuperDimmingScene (586)` --Perform Task--> `_LoadedPWMToggle (649)`
- `_SuperDimmingScene (586)` --Perform Task--> `_PrivilegeDetection V5 (Java) (378)`
- `_SuperDimmingScene (586)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `_SuperDimmingScene (586)` --Perform Task--> `_LoadedDimmingToggle (587)`
- `_SaveButtonDimming (588)` --Perform Task--> `_DimmingApplyToggle (589)`
- `_DimmingApplyToggle (589)` --Perform Task--> `Dynamic Scale V13 (Java) App Version (90)`
- `_DimmingApplyToggle (589)` --Perform Task--> `Disable Super Dimming (Privileged) (645)`
- `_DimmingApplyToggle (589)` --Perform Task--> `Set Initial Brightness (Java) V3 (618)`
- `_DimmingApplyToggle (589)` --Perform Task--> `Dimming Decider (578)`
- `unnamed_591 (591)` --Perform Task--> `_LoadedDimmingToggle (587)`
- `_KeyEventBackMenu (601)` --Perform Task--> `Advanced Auto Brightness (580)`
- `_KeyEventBackMenu (601)` --Perform Task--> `_ShowProfileScene (622)`
- `_KeyEventBackMenu (601)` --Perform Task--> `_ReactivityScene (575)`
- `_KeyEventBackMenu (601)` --Perform Task--> `_MiscScene (565)`
- `_KeyEventBackMenu (601)` --Perform Task--> `_ExperimentScene (540)`
- `_KeyEventBackMenu (601)` --Perform Task--> `_SuperDimmingScene (586)`
- `unnamed_603 (603)` --Perform Task--> `_LoadedExperimentTogglesV2 (558)`
- `unnamed_606 (606)` --Perform Task--> `_KeyEventBackMenu (601)`
- `unnamed_613 (613)` --Perform Task--> `_UpdateBrightnessFormulae (659)`
- `unnamed_613 (613)` --Perform Task--> `_UpdateStaticSceneElements (571)`
- `unnamed_613 (613)` --Perform Task--> `_RedInvalidFormulae (583)`
- `unnamed_614 (614)` --Perform Task--> `_UpdateBrightnessFormulae (659)`
- `unnamed_614 (614)` --Perform Task--> `_UpdateStaticSceneElements (571)`
- `unnamed_614 (614)` --Perform Task--> `_RedInvalidFormulae (583)`
- `unnamed_615 (615)` --Perform Task--> `_UpdateBrightnessFormulae (659)`
- `unnamed_615 (615)` --Perform Task--> `_UpdateStaticSceneElements (571)`
- `unnamed_615 (615)` --Perform Task--> `_RedInvalidFormulae (583)`
- `unnamed_616 (616)` --Perform Task--> `_UpdateBrightnessFormulae (659)`
- `unnamed_616 (616)` --Perform Task--> `_UpdateStaticSceneElements (571)`
- `unnamed_616 (616)` --Perform Task--> `_RedInvalidFormulae (583)`
- `unnamed_617 (617)` --Perform Task--> `_UpdateBrightnessFormulae (659)`
- `unnamed_617 (617)` --Perform Task--> `_UpdateStaticSceneElements (571)`
- `unnamed_617 (617)` --Perform Task--> `_RedInvalidFormulae (583)`
- `Set Initial Brightness (Java) V3 (618)` --Perform Task--> `Initialize AAB Defaults (570)`
- `Set Initial Brightness (Java) V3 (618)` --Perform Task--> `Map Lux to Brightness (Java) V2 (661)`
- `Set Initial Brightness (Java) V3 (618)` --Perform Task--> `Dynamic Scale V13 (Java) App Version (90)`
- `Set Initial Brightness (Java) V3 (618)` --Perform Task--> `Dimming Decider (578)`
- `Set Initial Brightness (Java) V3 (618)` --Perform Task--> `_ContextF5NetLoc V8 (631)`
- `Set Initial Brightness (Java) V3 (618)` --Perform Task--> `_EvaluateContexts V2 (43)`
- `unnamed_621 (621)` --Perform Task--> `Advanced Auto Brightness (580)`
- `unnamed_621 (621)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `unnamed_621 (621)` --Perform Task--> `_LoadedMainToggle (547)`
- `_ContextManager (623)` --Perform Task--> `_EvaluateContexts V2 (43)`
- `_ContextResume (626)` --Perform Task--> `_QSToggleAABService V2 (551)`
- `_ContextResume (626)` --Perform Task--> `_EvaluateContexts V2 (43)`
- `_ContextLocModeRead (628)` --Perform Task--> `_LocModeChanger (559)`
- `_ContextLocnListener V4 (630)` --Perform Task--> `_ContextLocModeRead (628)`
- `_ContextLocnListener V4 (630)` --Perform Task--> `_LocModeChanger (559)`
- `_ContextF5NetLoc V8 (631)` --Perform Task--> `_ContextLocnListener V4 (630)`
- `_GetWifiForContext (633)` --Perform Task--> `_GetWifiNoLocation V3 (105)`
- `_ProfileManager (637)` --Perform Task--> `Set Initial Brightness (Java) V3 (618)`
- `_ProfileManager (637)` --Perform Task--> `_CreateDefaultProfiles (592)`
- `_DimmingUIToggle (638)` --Perform Task--> `_SuperDimmingScene (586)`
- `ARGB To Hex (639)` --Perform Task--> `_ShowColorFilter (579)`
- `Calculate Super Dimming (Privileged) V4 (646)` --Perform Task--> `Apply Dimming (Privileged) (650)`
- `Calculate Super Dimming (Unprivileged) V3 (647)` --Perform Task--> `Apply Dimming (Unprivileged) (653)`
- `Calculate Super Dimming (Unprivileged) V3 (647)` --Perform Task--> `ARGB To Hex (639)`
- `_PWMToggle (648)` --Perform Task--> `_SuperDimmingScene (586)`
- `Apply Dimming (Privileged) (650)` --Perform Task--> `Disable Super Dimming (Privileged) (645)`
- `unnamed_651 (651)` --Perform Task--> `_SuggestCurveParameters V23 (Hybrid) (41)`
- `_GenerateCompressionGraph (Java) (657)` --Perform Task--> `_ChartJsChunks (581)`
- `Map Lux to Brightness (Java) V2 (661)` --Perform Task--> `Dynamic Range Compressed Scale (Java) V2 (548)`
- `Map Lux to Brightness (Java) V2 (661)` --Perform Task--> `Software Dimming V2 (700)`
- `Map Lux to Brightness (Java) V2 (661)` --Perform Task--> `Calculate Animation (Java) V2 (543)`
- `Map Lux to Brightness (Java) V2 (661)` --Perform Task--> `Smooth DC-Like Brightness Transition V5 (Java) (698)`
- `Map Lux to Brightness (Java) V2 (661)` --Perform Task--> `Disable Super Dimming (Privileged) (645)`
- `Map Lux to Brightness (Java) V2 (661)` --Perform Task--> `Disable Super Dimming (Unprivileged) (654)`
- `Map Lux to Brightness (Java) V2 (661)` --Perform Task--> `Dimming Decider (578)`
- `Map Lux to Brightness (Java) V2 (661)` --Perform Task--> `Calculate Animation (Java) V2 (543)`
- `Map Lux to Brightness (Java) V2 (661)` --Perform Task--> `Smooth Brightness Transition V5 (Java) (696)`
- `_GenerateGraph (Java) (663)` --Perform Task--> `_ChartJsChunks (581)`
- `unnamed_665 (665)` --Perform Task--> `_SaveButtonExperiment (541)`
- `unnamed_666 (666)` --Perform Task--> `_ExitButton (656)`
- `unnamed_667 (667)` --Perform Task--> `_ExitButton (656)`
- `unnamed_669 (669)` --Perform Task--> `_SaveButtonExperiment (541)`
- `unnamed_671 (671)` --Perform Task--> `_LoadedExperimentTogglesV2 (558)`
- `unnamed_674 (674)` --Perform Task--> `Dynamic Scale V13 (Java) App Version (90)`
- `unnamed_674 (674)` --Perform Task--> `_GenerateCircadianGraph V8 (Java) (549)`
- `unnamed_674 (674)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `unnamed_675 (675)` --Perform Task--> `Initialize AAB Defaults (570)`
- `unnamed_675 (675)` --Perform Task--> `_ExperimentScene (540)`
- `unnamed_676 (676)` --Perform Task--> `_ExperimentUIToggle (542)`
- `unnamed_677 (677)` --Perform Task--> `_ExperimentUIToggle (542)`
- `unnamed_679 (679)` --Perform Task--> `_SaveButtonExperiment (541)`
- `unnamed_680 (680)` --Perform Task--> `_LoadedExperimentTogglesV2 (558)`
- `unnamed_685 (685)` --Perform Task--> `_MenuScene (562)`
- `unnamed_686 (686)` --Perform Task--> `_GenerateCompressionGraph (Java) (657)`
- `unnamed_686 (686)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `unnamed_693 (693)` --Perform Task--> `_NotifyToggle (692)`
- `unnamed_695 (695)` --Perform Task--> `_NotifyToggle (692)`
- `unnamed_697 (697)` --Perform Task--> `_QSExperimentUIToggle (552)`
- `unnamed_699 (699)` --Perform Task--> `_QSExperimentUIToggle (552)`
- `_GenerateReactivityGraph (Java) (703)` --Perform Task--> `_ChartJsChunks (581)`
- `_GenerateCircadianDimmingGraph (Java) (705)` --Perform Task--> `_ChartJsChunks (581)`
- `_Updates (706)` --Perform Task--> `_ProfileManager (637)`
- `_Updates (706)` --Perform Task--> `_CreateLogo (619)`
- `unnamed_710 (710)` --Perform Task--> `_SaveButtonReactivity (577)`
- `unnamed_717 (717)` --Perform Task--> `_MenuScene (562)`
- `unnamed_718 (718)` --Perform Task--> `_MenuScene (562)`
- `unnamed_724 (724)` --Perform Task--> `_UpdateBrightnessFormulae (659)`
- `unnamed_724 (724)` --Perform Task--> `_UpdateStaticSceneElements (571)`
- `unnamed_724 (724)` --Perform Task--> `_RedInvalidFormulae (583)`
- `unnamed_724 (724)` --Perform Task--> `_GenerateGraph (Java) (663)`
- `unnamed_724 (724)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `unnamed_731 (731)` --Perform Task--> `_ReactivityToggle (555)`
- `unnamed_733 (733)` --Perform Task--> `_ReactivityToggle (555)`
- `unnamed_735 (735)` --Perform Task--> `Initialize AAB Defaults (570)`
- `unnamed_735 (735)` --Perform Task--> `_ReactivityScene (575)`
- `unnamed_738 (738)` --Perform Task--> `Initialize AAB Defaults (570)`
- `unnamed_738 (738)` --Perform Task--> `_MiscScene (565)`
- `unnamed_743 (743)` --Perform Task--> `_GenerateAlphaGraph (Java) (557)`
- `unnamed_743 (743)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `unnamed_746 (746)` --Perform Task--> `_SaveButtonMisc (564)`
- `unnamed_748 (748)` --Perform Task--> `_AdaptiveBrightnessSceneSize V4 (620)`
- `unnamed_748 (748)` --Perform Task--> `_LoadedMiscAuto (576)`
- `unnamed_752 (752)` --Perform Task--> `_RedInvalidFormulae (583)`
- `Dynamic Scale V13 (Java) App Version (90)` --Perform Task--> `Evaluate Light Change (Java) V2 (544)`

## 3) Variable inventory (AAB/as/app prefixes)

- `%AAB_AnimSteps`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=20
- `%AAB_ChartJs`: type=variable-ref, owner=_ChartJsChunks, lifetime=global_tasker_variable, default=None
- `%AAB_ContextJSONCache`: type=unknown, owner=_ResetContextCacheDaily, lifetime=global_tasker_variable, default=None
- `%AAB_ContextLocMode`: type=integer, owner=_ContextLocModeRead, lifetime=global_tasker_variable, default=-1
- `%AAB_ContextOverride`: type=boolean/enum, owner=_ContextResume, lifetime=global_tasker_variable, default=false
- `%AAB_CurrentActiveProfile`: type=variable-ref, owner=_EvaluateContexts V2, lifetime=global_tasker_variable, default=None
- `%AAB_CurrentBright`: type=variable-ref, owner=Map Lux to Brightness (Java) V2, lifetime=global_tasker_variable, default=None
- `%AAB_CycleStart`: type=variable-ref, owner=Set Initial Brightness (Java) V3, lifetime=global_tasker_variable, default=None
- `%AAB_CycleTime`: type=unknown, owner=Smooth Brightness Transition V5 (Java), lifetime=global_tasker_variable, default=None
- `%AAB_CycleTotal`: type=variable-ref, owner=Evaluate Light Change (Java) V2, lifetime=global_tasker_variable, default=None
- `%AAB_Date`: type=variable-ref, owner=_ExperimentSetDate, lifetime=global_tasker_variable, default=None
- `%AAB_Debug`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=0
- `%AAB_DefaultThrottle`: type=variable-ref, owner=Reset Throttle, lifetime=global_tasker_variable, default=1000
- `%AAB_DeltaFactor`: type=float, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=1.8
- `%AAB_DetectOverrides`: type=boolean/enum, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=Off
- `%AAB_DimDynamic`: type=unknown, owner=Calculate Super Dimming (Privileged) V4, lifetime=global_tasker_variable, default=None
- `%AAB_DimSpread`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=100
- `%AAB_DimmingCurrent`: type=variable-ref, owner=Apply Dimming (Privileged), lifetime=global_tasker_variable, default=0
- `%AAB_DimmingDS`: type=integer, owner=Apply Dimming (Privileged), lifetime=global_tasker_variable, default=0
- `%AAB_DimmingEnabled`: type=boolean/enum, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=false
- `%AAB_DimmingExponent`: type=float, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=2.5
- `%AAB_DimmingStatus`: type=integer, owner=ARGB To Hex, lifetime=global_tasker_variable, default=0
- `%AAB_DimmingStrength`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=25
- `%AAB_DimmingStrengthCurr`: type=variable-ref, owner=Apply Dimming (Privileged), lifetime=global_tasker_variable, default=None
- `%AAB_DimmingThreshold`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=15
- `%AAB_EveningDuration`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_EveningEnd`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_EveningStart`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_Form1A`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=5
- `%AAB_Form2A`: type=variable-ref, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=None
- `%AAB_Form2B`: type=float, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=8.8
- `%AAB_Form2C`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=18
- `%AAB_Form2D`: type=variable-ref, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=None
- `%AAB_Form3A`: type=string, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=None
- `%AAB_HTML_Graph`: type=variable-ref, owner=_GenerateGraph (Java), lifetime=global_tasker_variable, default=None
- `%AAB_HTML_Graph2`: type=variable-ref, owner=_GenerateReactivityGraph (Java), lifetime=global_tasker_variable, default=None
- `%AAB_HTML_Graph3`: type=variable-ref, owner=_GenerateAlphaGraph (Java), lifetime=global_tasker_variable, default=None
- `%AAB_HTML_Graph4`: type=variable-ref, owner=_GenerateCircadianGraph V8 (Java), lifetime=global_tasker_variable, default=None
- `%AAB_HTML_Graph5`: type=variable-ref, owner=_GenerateCompressionGraph (Java), lifetime=global_tasker_variable, default=None
- `%AAB_HTML_Graph6`: type=variable-ref, owner=_GenerateDimmingCurveGraph (Java), lifetime=global_tasker_variable, default=None
- `%AAB_HTML_Graph7`: type=variable-ref, owner=_GenerateCircadianDimmingGraph (Java), lifetime=global_tasker_variable, default=None
- `%AAB_HTML_Graph8`: type=variable-ref, owner=_CalibratePowerDraw, lifetime=global_tasker_variable, default=None
- `%AAB_HexOverlay`: type=string, owner=ARGB To Hex, lifetime=global_tasker_variable, default=None
- `%AAB_Initializing`: type=boolean/enum, owner=Set Initial Brightness (Java) V3, lifetime=global_tasker_variable, default=true
- `%AAB_JavaDialogResponse`: type=unknown, owner=_DeleteOverridePoint, lifetime=global_tasker_variable, default=None
- `%AAB_LastAnimation`: type=unknown, owner=Smooth Brightness Transition V5 (Java), lifetime=global_tasker_variable, default=None
- `%AAB_LastRawLux`: type=variable-ref, owner=Set Initial Brightness (Java) V3, lifetime=global_tasker_variable, default=None
- `%AAB_LastSensorAccuracy`: type=variable-ref, owner=Set Initial Brightness (Java) V3, lifetime=global_tasker_variable, default=None
- `%AAB_Latitude`: type=variable-ref, owner=_ExperimentSetDate, lifetime=global_tasker_variable, default=None
- `%AAB_LocnBackOff`: type=unknown, owner=_ContextF5NetLoc V8, lifetime=global_tasker_variable, default=None
- `%AAB_LocnLog`: type=unknown, owner=_ContextLocnListener V4, lifetime=global_tasker_variable, default=None
- `%AAB_Longitude`: type=variable-ref, owner=_ExperimentSetDate, lifetime=global_tasker_variable, default=None
- `%AAB_MainLoop`: type=integer, owner=Evaluate Light Change (Java) V2, lifetime=global_tasker_variable, default=0
- `%AAB_Manual_Override`: type=boolean/enum, owner=Manual Override, lifetime=global_tasker_variable, default=true
- `%AAB_MaxBright`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=255
- `%AAB_MaxWait`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=65
- `%AAB_MinBright`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=10
- `%AAB_MinThrottle`: type=unknown, owner=_MiscScene, lifetime=global_tasker_variable, default=None
- `%AAB_MinWait`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=25
- `%AAB_MorningDuration`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_MorningEnd`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_MorningStart`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_NetLocation`: type=unknown, owner=Reset Brightness and State, lifetime=global_tasker_variable, default=None
- `%AAB_NotifyUse`: type=boolean/enum, owner=_NotifyToggle, lifetime=global_tasker_variable, default=true
- `%AAB_NowSS`: type=variable-ref, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_Offset`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=0
- `%AAB_Overrides`: type=unknown, owner=Process Overrides, lifetime=global_tasker_variable, default=None
- `%AAB_PWMExp`: type=float, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=0.8
- `%AAB_PWMSensitive`: type=boolean/enum, owner=_DimmingUIToggle, lifetime=global_tasker_variable, default=false
- `%AAB_Package`: type=unknown, owner=_LearnWriteSecure, lifetime=global_tasker_variable, default=None
- `%AAB_PermGranted`: type=unknown, owner=_AskPermissionsV7, lifetime=global_tasker_variable, default=None
- `%AAB_PolarState`: type=boolean/enum, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=true
- `%AAB_PrevBright`: type=variable-ref, owner=Smooth DC-Like Brightness Transition V5 (Java), lifetime=global_tasker_variable, default=None
- `%AAB_Privilege`: type=string, owner=_PrivilegeDetection V5 (Java), lifetime=global_tasker_variable, default=None
- `%AAB_ProfileUser`: type=variable-ref, owner=_ProfileManager, lifetime=global_tasker_variable, default=None
- `%AAB_Proximity`: type=string, owner=Detect Proximity, lifetime=global_tasker_variable, default=near
- `%AAB_QSUse`: type=boolean/enum, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=false
- `%AAB_ResumeTapped`: type=boolean/enum, owner=Manual Override, lifetime=global_tasker_variable, default=Off
- `%AAB_Scale`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=1
- `%AAB_ScaleDynamic`: type=unknown, owner=Dynamic Range Compressed Scale (Java) V2, lifetime=global_tasker_variable, default=None
- `%AAB_ScaleDynamicCompress`: type=unknown, owner=Process Overrides, lifetime=global_tasker_variable, default=None
- `%AAB_ScaleSpread`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=15
- `%AAB_ScaleSteepness`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=6
- `%AAB_ScaleTaperMidpoint`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=190
- `%AAB_ScaleTaperSteepness`: type=float, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=0.075
- `%AAB_ScaleTransitionFactor`: type=float, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=0.1
- `%AAB_ScalingUse`: type=boolean/enum, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=false
- `%AAB_Scenebg`: type=string, owner=Advanced Auto Brightness, lifetime=global_tasker_variable, default=<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no, viewport-fit=cover">
    <title>AAB Background</title>
    <style>
        * {
            -webkit-tap-highlight-color: transparent;
            box-sizing: border-box;
        }

        body {
            margin: 0;
            background-color: #333333;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
            height: 100vh;
            width: 100vw;
            overflow: hidden;
        }

        .banner {
            background-color: #007C63;
            color: #FFFFFF;
            padding: clamp(20px, 7vw, 35px) 4vw;
            
            box-shadow: 0 4px 8px rgba(0,0,0,0.3);
            display: flex;
            align-items: center;
            justify-content: flex-start;
            max-height: 20vh;
            overflow: hidden;
        }
        
        .banner h1 {
            margin: 0 0 0 5vw;
            font-weight: 500;
            line-height: 1;
            white-space: nowrap;
            font-size: clamp(16px, 5.5vw, 32px);
        }

        .placeholder {
            width: clamp(24px, 7vw, 36px);
            height: clamp(20px, 6vw, 30px);
            
            flex-shrink: 0;
        }
    </style>
</head>
<body>

    <div class="banner">
        <div class="placeholder"></div>
        <h1>Advanced Auto Brightness</h1>
    </div>
</body>
</html>
- `%AAB_Service`: type=boolean/enum, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=On
- `%AAB_SettingsBGone`: type=string, owner=_Updates, lifetime=global_tasker_variable, default=None
- `%AAB_SettingsBGtwo`: type=string, owner=_Updates, lifetime=global_tasker_variable, default=);">
            <span></span>
            <span></span>
            <span></span>
        </div>

        <h1>Advanced Auto Brightness</h1>
    </div>
</body>
</html>
- `%AAB_SetupComplete`: type=integer, owner=Advanced Auto Brightness, lifetime=global_tasker_variable, default=1
- `%AAB_SetupTitle`: type=string, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=Advanced Auto Brightness Setup
- `%AAB_SunLastDate`: type=variable-ref, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_Sundawn`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_Sundusk`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_Sunlightduration`: type=variable-ref, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_Sunnoon`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_Sunrise`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_Sunset`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_Test`: type=variable-ref, owner=_SuggestCurveParameters V23 (Hybrid), lifetime=global_tasker_variable, default=None
- `%AAB_ThreshAbsHigh`: type=unknown, owner=Evaluate Light Change (Java) V2, lifetime=global_tasker_variable, default=None
- `%AAB_ThreshAbsLow`: type=unknown, owner=Evaluate Light Change (Java) V2, lifetime=global_tasker_variable, default=None
- `%AAB_ThreshBright`: type=float, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=0.08
- `%AAB_ThreshDark`: type=float, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=0.3
- `%AAB_ThreshDim`: type=float, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=0.25
- `%AAB_ThreshDynamic`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=5
- `%AAB_ThreshMidpoint`: type=string, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=None
- `%AAB_ThreshSteepness`: type=float, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=2.1
- `%AAB_Throttle`: type=variable-ref, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=None
- `%AAB_TrustUnreliable`: type=boolean/enum, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=Off
- `%AAB_Version`: type=float, owner=_Updates, lifetime=global_tasker_variable, default=3.3
- `%AAB_Zone1End`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=35
- `%AAB_Zone2End`: type=integer, owner=Initialize AAB Defaults, lifetime=global_tasker_variable, default=10000
- `%AAB_calc_dawn`: type=variable-ref, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_calc_dusk`: type=variable-ref, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_calc_noon`: type=variable-ref, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_calc_sunrise`: type=unknown, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%AAB_calc_sunset`: type=variable-ref, owner=Dynamic Scale V13 (Java) App Version, lifetime=global_tasker_variable, default=None
- `%app_list_json`: type=unknown, owner=_AppPicker, lifetime=global_tasker_variable, default=None
- `%as_accuracy`: type=unknown, owner=Process Sensor Event (Java), lifetime=global_tasker_variable, default=None
- `%as_values1`: type=unknown, owner=Process Sensor Event (Java), lifetime=global_tasker_variable, default=None

## 4) Side effects catalog

- [brightness_write] task `_PrivilegeDetection V5 (Java)` action#0: `import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;`
- [overlay_display] task `_PrivilegeDetection V5 (Java)` action#0: `import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;`
- [permission_prompt] task `_PrivilegeDetection V5 (Java)` action#0: `import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;`
- [brightness_write] task `_PrivilegeDetection V5 (Java)` action#7: `Advanced Auto Brightness | Please tap learn in order to learn how to grant Write Secure Settings permissions to this app. | aab_setup_notification`
- [notification_update] task `_PrivilegeDetection V5 (Java)` action#7: `Advanced Auto Brightness | Please tap learn in order to learn how to grant Write Secure Settings permissions to this app. | aab_setup_notification`
- [permission_prompt] task `_PrivilegeDetection V5 (Java)` action#7: `Advanced Auto Brightness | Please tap learn in order to learn how to grant Write Secure Settings permissions to this app. | aab_setup_notification`
- [overlay_display] task `_PrivilegeDetection V5 (Java)` action#9: `⚠️ Unprivileged will draw a semi-transparent overlay and eat your battery. Not recommended! Please check notification for an alternative. | aab_toast | #FF007C63 | 7000`
- [notification_update] task `_PrivilegeDetection V5 (Java)` action#9: `⚠️ Unprivileged will draw a semi-transparent overlay and eat your battery. Not recommended! Please check notification for an alternative. | aab_toast | #FF007C63 | 7000`
- [overlay_display] task `unnamed_411` action#0: `%ui_body_elements=Override,thresh_dark_label,dark_threshold,zone_1_end_label,zone_1_end_calculated,thresh_dim_label,dim_threshold,thresh_bright_label,bright_threshold,thresh_steepn`
- [overlay_display] task `unnamed_411` action#1: `_AdaptiveBrightnessSceneSize V4 | AAB Reactivity Settings | AAB Reactivity Graph`
- [file_io] task `_EvaluateContexts V2` action#3: `import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.nio.file.Files`
- [overlay_display] task `unnamed_462` action#0: `_MenuScene`
- [overlay_display] task `unnamed_462` action#2: `AAB Debug Scene`
- [overlay_display] task `unnamed_473` action#1: `%ui_body_elements=save_button,back_scene | =`
- [overlay_display] task `unnamed_473` action#2: `_AdaptiveBrightnessSceneSize V4 | AAB Reactivity Graph`
- [overlay_display] task `unnamed_500` action#6: `_SuperDimmingScene`
- [overlay_display] task `unnamed_509` action#3: `⚠️ Unprivileged super dimming will use a semi transparent overlay. Battery drain might be excessive! | #FF007C63`
- [overlay_display] task `unnamed_511` action#3: `⚠️ Unprivileged super dimming will use a semi transparent overlay. Battery drain might be excessive! | #FF007C63`
- [overlay_display] task `unnamed_513` action#5: `%ui_body_elements=save_button,back_scene | =`
- [overlay_display] task `unnamed_513` action#6: `_AdaptiveBrightnessSceneSize V4 | AAB Dimming Graph`
- [overlay_display] task `unnamed_517` action#7: `%ui_body_elements=save_button,back_scene | =`
- [overlay_display] task `unnamed_517` action#8: `_AdaptiveBrightnessSceneSize V4 | AAB Circadian Dimming Graph`
- [overlay_display] task `_CalibratePowerDraw` action#3: `%ui_body_elements=back_scene | =`
- [overlay_display] task `_CalibratePowerDraw` action#4: `_AdaptiveBrightnessSceneSize V4 | AAB Power Draw Graph`
- [overlay_display] task `unnamed_532` action#0: `AAB Debug Scene`
- [overlay_display] task `unnamed_532` action#2: `AAB Debug Scene`
- [overlay_display] task `unnamed_532` action#3: `AAB Debug Scene`
- [overlay_display] task `_ExperimentScene` action#1: `%ui_body_elements=spread_label,scale,save_button,exit_scenes,draw_graph4,undo_button,transition_factor_label,curve_steepness_label,transition,steepness,experimental_label,draw_grap`
- [overlay_display] task `_ExperimentScene` action#9: `_AdaptiveBrightnessSceneSize V4 | AAB Experiment Settings | AAB Experiment Graph,AAB Taper Graph`
- [overlay_display] task `unnamed_550` action#0: `AAB Debug Scene`
- [notification_update] task `_QSToggleAABService V2` action#3: `Repost Foreground Notification`
- [notification_update] task `_QSToggleAABService V2` action#32: `Repost Foreground Notification`
- [notification_update] task `_QSToggleAABService V2` action#41: `_ForegroundNotification`
- [permission_prompt] task `_AskPermissionsV7` action#1: `%orig_perm | %AAB_PermGranted`
- [overlay_display] task `_AskPermissionsV7` action#8: `import android.provider.Settings;
             import android.content.Intent;
             import android.net.Uri;
             import android.app.AlertDialog;
             import `
- [notification_update] task `_AskPermissionsV7` action#8: `import android.provider.Settings;
             import android.content.Intent;
             import android.net.Uri;
             import android.app.AlertDialog;
             import `
- [permission_prompt] task `_AskPermissionsV7` action#8: `import android.provider.Settings;
             import android.content.Intent;
             import android.net.Uri;
             import android.app.AlertDialog;
             import `
- [permission_prompt] task `_AskPermissionsV7` action#10: `All permissions granted! | aab_toast | #FF007C63`
- [overlay_display] task `_MiscScene` action#1: `%ui_body_elements=min_bright_label,Minimum Brightness Slider,max_bright_label,Maximum Brightness,scale_label,scale,offset_label,offset,Anim_steps_label,Animation Steps,min_wait_lab`
- [notification_update] task `_MiscScene` action#1: `%ui_body_elements=min_bright_label,Minimum Brightness Slider,max_bright_label,Maximum Brightness,scale_label,scale,offset_label,offset,Anim_steps_label,Animation Steps,min_wait_lab`
- [overlay_display] task `_MiscScene` action#2: `_AdaptiveBrightnessSceneSize V4 | AAB Misc Settings | AAB Alpha Graph`
- [notification_update] task `Manual Override` action#9: `Manual brightness override detected or Android's auto-brightness enabled. The service is now paused. Tap the notification to continue the service. | #FFFFFFFF | Bottom | aab_toast `
- [notification_update] task `Manual Override` action#11: `Advanced Auto Brightness Paused | Brightness manually set or Android auto brightness enabled. Expand notification and tap "tap here to resume." | aab_override_notification`
- [notification_update] task `Manual Override` action#16: `Repost Paused Notification`
- [notification_update] task `Manual Override` action#24: `Advanced Auto Brightness Paused | Brightness manually set or Android auto brightness enabled. Expand notification and tap "tap here to resume." | aab_override_notification`
- [notification_update] task `Initialize AAB Defaults` action#56: `Repost Paused Notification`
- [notification_update] task `Initialize AAB Defaults` action#60: `%AAB_SetupTitle | Defaults initialized. To finish setup, please turn your screen off and back on. | aab_setup_notification`
- [overlay_display] task `_ReactivityScene` action#1: `%ui_body_elements=Override,thresh_dark_label,dark_threshold,zone_1_end_label,zone_1_end_calculated,thresh_dim_label,dim_threshold,thresh_bright_label,bright_threshold,thresh_steepn`
- [overlay_display] task `_ReactivityScene` action#2: `_AdaptiveBrightnessSceneSize V4 | AAB Reactivity Settings | AAB Reactivity Graph`
- [notification_update] task `_LoadedMiscAuto` action#3: `AAB Misc Settings | Notify_on_green`
- [notification_update] task `_LoadedMiscAuto` action#8: `AAB Misc Settings | Notify_on_green`
- [permission_prompt] task `Advanced Auto Brightness` action#1: `_AskPermissionsV7`
- [overlay_display] task `Advanced Auto Brightness` action#6: `_ColorSuggestionsScene`
- [overlay_display] task `Advanced Auto Brightness` action#7: `%ui_h1_elements=switch_description
%ui_body_elements=form1a_label,form1a,form2d_label,End_zone_1,form2a_label,Form2a_calculated,form2b_label,form2b,form2c_label,form2c,end_zone_2 l`
- [overlay_display] task `Advanced Auto Brightness` action#8: `_AdaptiveBrightnessSceneSize V4 | AAB Brightness Settings | AAB Brightness Graph`
- [overlay_display] task `Advanced Auto Brightness` action#16: `%AAB_Scenebg | <!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no, viewpo`
- [notification_update] task `_ChartJsChunks` action#0: `%chartjs | /*!
 * Chart.js v4.5.0
 * https://www.chartjs.org
 * (c) 2025 Chart.js Contributors
 * Released under the MIT License
 */
!function(t,e){"object"==typeof exports&&"undef`
- [notification_update] task `_ChartJsChunks` action#2: `%chartjs11 | yout(o),s||u(n,(t=>{t.reset()})),this._updateDatasets(t),this.notifyPlugins("afterUpdate",{mode:t}),this._layers.sort(kn("z","_idx"));const{_active:a,_lastEvent:r}=thi`
- [notification_update] task `_ChartJsChunks` action#11: `%chartjs20 | xt(this.getContext()),s=i.enabled&&e.options.animation&&i.animations,n=new Ts(this.chart,s);return s._cacheable&&(this._cachedAnimations=Object.freeze(n)),n}getContext`
- [notification_update] task `_ChartJsChunks` action#16: `%chartjs6 | s,s),e=e&&!ms(i.addedNodes,s);e&&i()}));return n.observe(document,{childList:!0,subtree:!0}),n}const _s=new Map;let ys=0;function vs(){const t=window.devicePixelRatio;t`
- [notification_update] task `_ChartJsChunks` action#18: `%chartjs8 | ),maxDefined:a(e)}}getMinMax(t){let e,{min:i,max:s,minDefined:n,maxDefined:o}=this.getUserBounds();if(n&&o)return{min:i,max:s};const a=this.getMatchingVisibleMetas();fo`
- [notification_update] task `_ChartJsChunks` action#19: `%chartjs9 | etYAxisLabelAlignment(f);k=t.textAlign,M=t.x}else if("right"===s){const t=this._getYAxisLabelAlignment(f);k=t.textAlign,M=t.x}else if("x"===e){if("center"===s)w=(t.top+`
- [notification_update] task `_ChartJsChunks` action#20: `%chartjs10 | aults&&a.push(e.defaults),t.createResolver(a,n,[""],{scriptable:!1,indexable:!1,allKeys:!0})}function ln(t,e){const i=ue.datasets[t]||{};return((e.datasets||{})[t]||{}`
- [notification_update] task `_ForegroundNotification` action#2: `Advanced Auto Brightness | Foreground notification. Please don't dismiss this notification. It's important for maintaining functionality. | aab_setup_notification`
- [notification_update] task `_ForegroundNotification` action#4: `Advanced Auto Brightness | Foreground notification. Please don't dismiss this notification. It's important for maintaining functionality. | aab_setup_notification`
- [overlay_display] task `_SuperDimmingScene` action#1: `%ui_body_elements=Strength_label,strength,dim_spread_label,spread,Dimming_Exponent_label,steepness,Dimming_Threshold_label,dimming threshold,superdimming_label,Privilege_label,priv`
- [overlay_display] task `_SuperDimmingScene` action#10: `_AdaptiveBrightnessSceneSize V4 | AAB Superdimming Settings | AAB Circadian Dimming Graph,AAB Dimming Graph`
- [file_io] task `_CreateDefaultProfiles` action#0: `import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;

/*
 * AAB Default Profile Generator
 * Gene`
- [file_io] task `_CreateDefaultProfiles` action#4: `Download/AAB/configs | *.json | %current_files`
- [file_io] task `_CreateDefaultProfiles` action#6: `AAB Profile | Profile Dashboard | javascript: files = '%current_files'.split(',') .map(f => f.split('/').pop().replace('.json', '')) .filter(n => n && n !== 'contexts') .sort(); re`
- [overlay_display] task `unnamed_600` action#2: `AAB Debug Scene`
- [overlay_display] task `_KeyEventBackMenu` action#3: `_ShowProfileScene`
- [overlay_display] task `_KeyEventBackMenu` action#8: `_ReactivityScene`
- [overlay_display] task `_KeyEventBackMenu` action#10: `_MiscScene`
- [overlay_display] task `_KeyEventBackMenu` action#12: `_ExperimentScene`
- [overlay_display] task `_KeyEventBackMenu` action#14: `_SuperDimmingScene`
- [overlay_display] task `unnamed_607` action#1: `AAB Superdimming Settings | %scene_status`
- [overlay_display] task `unnamed_613` action#3: `_UpdateStaticSceneElements`
- [overlay_display] task `unnamed_614` action#3: `_UpdateStaticSceneElements`
- [overlay_display] task `unnamed_615` action#3: `_UpdateStaticSceneElements`
- [overlay_display] task `unnamed_616` action#8: `_UpdateStaticSceneElements`
- [overlay_display] task `unnamed_617` action#5: `_UpdateStaticSceneElements`
- [file_io] task `_CreateLogo` action#7: `/storage/emulated/0/Download/AAB/logo.png | %file_exists`
- [overlay_display] task `unnamed_621` action#5: `%ui_h1_elements=switch_description
%ui_body_elements=form1a_label,form1a,form2d_label,End_zone_1,form2a_label,Form2a_calculated,form2b_label,form2b,form2c_label,form2c,end_zone_2 l`
- [overlay_display] task `unnamed_621` action#6: `_AdaptiveBrightnessSceneSize V4 | AAB Brightness Settings | AAB Brightness Graph`
- [file_io] task `_ShowProfileScene` action#0: `Download/AAB/configs/contexts.json | %aab_contexts_json`
- [file_io] task `_ShowProfileScene` action#1: `Download/AAB/configs/ | *.json | %files`
- [file_io] task `_ContextManager` action#1: `Download/AAB/configs/contexts.json | %exists`
- [file_io] task `_ContextManager` action#7: `Download/AAB/configs/contexts.json | []`
- [file_io] task `_ContextManager` action#9: `import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Hash`
- [permission_prompt] task `_ContextManager` action#9: `import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Hash`
- [file_io] task `_AppPicker` action#0: `import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawabl`
- [notification_update] task `_ContextResume` action#8: `org.json.JSONObject state = new org.json.JSONObject();

String[] keys = {"AAB_Form1A", "AAB_Zone1End", "AAB_Form2A", "AAB_Form2B", "AAB_Form2C", "AAB_Zone2End", "AAB_Form3A", "AAB_`
- [file_io] task `_ContextResume` action#8: `org.json.JSONObject state = new org.json.JSONObject();

String[] keys = {"AAB_Form1A", "AAB_Zone1End", "AAB_Form2A", "AAB_Form2B", "AAB_Form2C", "AAB_Zone2End", "AAB_Form3A", "AAB_`
- [overlay_display] task `_ShowDebugScene` action#0: `AAB Debug Scene`
- [overlay_display] task `_ShowDebugScene` action#2: `AAB Debug Scene`
- [overlay_display] task `_ShowDebugScene` action#3: `AAB Debug Scene`
- [overlay_display] task `_DeleteOverridePoint` action#1: `/* --- Java Code to Show a Native Overlay Dialog --- */
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;`
- [file_io] task `_ProfileManager` action#5: `%profile_name | ^.*/|\.json$`
- [file_io] task `_ProfileManager` action#9: `import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;
import java.math.BigDecimal;
import java.math.`
- [overlay_display] task `_DimmingUIToggle` action#18: `_SuperDimmingScene`
- [overlay_display] task `ARGB To Hex` action#7: `Preview of overlay color for current brightness and/or time of day. | #FF007C63 | aab_debug | %AAB_HexOverlay`
- [overlay_display] task `ARGB To Hex` action#12: `%AAB_HexOverlay | #%hex_a%append`
- [permission_prompt] task `_LearnWriteSecure` action#4: `import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.ClipboardManager;
import`
- [permission_prompt] task `_LearnWriteSecure` action#5: `android.permission.WRITE_SECURE_SETTINGS | Please follow the instructions in the next pop up to grant Write Secure Settings permissions to %AAB_Package. This will enable a more eff`
- [overlay_display] task `_SuperDimmingLongTap` action#3: `Displays your current privilege. 
⚠️ Unprivileged will draw a semi-transparent overlay and eat your battery. Not recommended to enable super dimming! | aab_toast | #FF007C63`
- [brightness_write] task `_SuperDimmingLongTap` action#14: `Advanced Auto Brightness | Please tap learn in order to learn how to grant Write Secure Settings permissions to this app. | aab_setup_notification`
- [notification_update] task `_SuperDimmingLongTap` action#14: `Advanced Auto Brightness | Please tap learn in order to learn how to grant Write Secure Settings permissions to this app. | aab_setup_notification`
- [permission_prompt] task `_SuperDimmingLongTap` action#14: `Advanced Auto Brightness | Please tap learn in order to learn how to grant Write Secure Settings permissions to this app. | aab_setup_notification`
- [overlay_display] task `_PWMToggle` action#3: `System will use an overlay to dim the screen. 

⚠️ Might cause increased battery drain! | Bottom | #FF007C63`
- [overlay_display] task `_PWMToggle` action#6: `_SuperDimmingScene`
- [overlay_display] task `_PWMToggle` action#15: `System will no longer use an overlay to dim the screen | Bottom | #FF007C63`
- [overlay_display] task `Disable Super Dimming (Unprivileged)` action#0: `%AAB_HexOverlay`
- [overlay_display] task `_ExitButton` action#18: `AAB Debug Scene`
- [overlay_display] task `unnamed_674` action#7: `%ui_body_elements=save_button,back_scene | =`
- [overlay_display] task `unnamed_674` action#8: `_AdaptiveBrightnessSceneSize V4 | AAB Experiment Graph`
- [overlay_display] task `unnamed_675` action#6: `_ExperimentScene`
- [notification_update] task `unnamed_682` action#0: `Use notifications to keep the service alive and show 'paused' notifications. | #FF007C63`
- [overlay_display] task `unnamed_685` action#0: `_MenuScene`
- [overlay_display] task `unnamed_686` action#2: `%ui_body_elements=save_button,back_scene | =`
- [overlay_display] task `unnamed_686` action#3: `_AdaptiveBrightnessSceneSize V4 | AAB Taper Graph`
- [notification_update] task `_NotifyToggle` action#1: `%AAB_NotifyUse | true`
- [notification_update] task `_NotifyToggle` action#2: `%AAB_NotifyUse | false`
- [notification_update] task `_NotifyToggle` action#3: `AAB Misc Settings | Notify_on_green`
- [notification_update] task `_NotifyToggle` action#4: `Notifications disabled!<br>
⚠️ Service <i>might</i> be stopped via aggressive battery management. | Bottom | #FF007C63`
- [notification_update] task `_NotifyToggle` action#9: `%AAB_NotifyUse | true`
- [notification_update] task `_NotifyToggle` action#10: `AAB Misc Settings | Notify_on_green`
- [notification_update] task `_NotifyToggle` action#11: `Notifications enabled! | Bottom | #FF007C63`
- [notification_update] task `unnamed_693` action#0: `_NotifyToggle`
- [notification_update] task `unnamed_695` action#0: `_NotifyToggle`
- [brightness_write] task `Smooth Brightness Transition V5 (Java)` action#3: `import android.provider.Settings;
import android.content.ContentResolver;

/* --- Safe Initialization Phase --- */

/* 1. Get Content Resolver */
cResolver = context.getContentReso`
- [brightness_write] task `Smooth DC-Like Brightness Transition V5 (Java)` action#8: `import java.util.HashMap;
import android.provider.Settings;
import android.content.ContentResolver;

/* --- Start Timer --- */
engineStartTime = System.currentTimeMillis();

/* ---`
- [overlay_display] task `Smooth DC-Like Brightness Transition V5 (Java)` action#8: `import java.util.HashMap;
import android.provider.Settings;
import android.content.ContentResolver;

/* --- Start Timer --- */
engineStartTime = System.currentTimeMillis();

/* ---`
- [notification_update] task `_Updates` action#1: `%AAB_NotifyUse | true`
- [file_io] task `_Updates` action#4: `Download/AAB/configs/Back-up_v3-2_%parseddate.json | %exists`
- [file_io] task `_Updates` action#8: `Download/AAB/configs/contexts.json | %exists`
- [file_io] task `_Updates` action#11: `Download/AAB/configs/contexts.json | []`
- [overlay_display] task `_Updates` action#14: `%AAB_SettingsBGone | <!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no, `
- [permission_prompt] task `_Updates` action#19: `%AAB_PermGranted`
- [overlay_display] task `unnamed_717` action#0: `_MenuScene`
- [overlay_display] task `unnamed_718` action#0: `_MenuScene`
- [overlay_display] task `unnamed_724` action#1: `_UpdateStaticSceneElements`
- [overlay_display] task `unnamed_724` action#17: `%ui_body_elements=save_button,back_scene_graph,suggest_graph | =`
- [overlay_display] task `unnamed_724` action#18: `_AdaptiveBrightnessSceneSize V4 | AAB Brightness Graph`
- [overlay_display] task `unnamed_735` action#7: `_ReactivityScene`
- [overlay_display] task `unnamed_738` action#6: `_MiscScene`
- [overlay_display] task `unnamed_743` action#1: `%ui_body_elements=save_button,back_scene | =`
- [overlay_display] task `unnamed_743` action#2: `_AdaptiveBrightnessSceneSize V4 | AAB Alpha Graph`
- [overlay_display] task `unnamed_748` action#0: `%ui_body_elements=min_bright_label,Minimum Brightness Slider,max_bright_label,Maximum Brightness,scale_label,scale,offset_label,offset,Anim_steps_label,Animation Steps,min_wait_lab`
- [notification_update] task `unnamed_748` action#0: `%ui_body_elements=min_bright_label,Minimum Brightness Slider,max_bright_label,Maximum Brightness,scale_label,scale,offset_label,offset,Anim_steps_label,Animation Steps,min_wait_lab`
- [overlay_display] task `unnamed_748` action#1: `_AdaptiveBrightnessSceneSize V4 | AAB Misc Settings | AAB Alpha Graph`
- [permission_prompt] task `Dynamic Scale V13 (Java) App Version` action#2: `android.permission.ACCESS_BACKGROUND_LOCATION | The dynamic scale engine requires location access in order to properly acquire times for solar events in your location.`
- [notification_update] task `Dynamic Scale V13 (Java) App Version` action#41: `Advanced Auto Brightness | Failed to retrieve Sun data today. Will use yesterday's data. Toggle 'use circadian scaling' to retry. | aab_privilege_notification`
- [permission_prompt] task `Dynamic Scale V13 (Java) App Version` action#45: `CheckMissingPermissions(android.permission.WRITE_SECURE_SETTINGS)`

## 5) Behavioral acceptance criteria

### Manual Override
- When screen_brightness changes externally and override detection is enabled, AAB transitions into manual override mode.
- While manual override is active, automatic brightness writes are suppressed until reset conditions clear.
### Context Adaptation
- Context events (app change, battery change, location/WiFi changes) trigger the shared context recomputation task.
- Recomputed context feeds subsequent brightness scaling decisions without requiring screen restart.
### Dimming
- Display-off hibernate profile triggers dimming/teardown task chain.
- Proximity and ambient light monitor profiles can lower target brightness under dark or near-object conditions.
### Circadian Scaling
- Dynamic Scale Engine profile evaluates time- and state-based conditions before applying scale factors.
- Scaling should be monotonic with configured nighttime constraints to avoid abrupt jumps.
### Foreground Notification Behavior
- Foreground notification is reposted when service is active and notification gating conditions are true.
- Paused notification variant is posted when manual/initialization gating indicates suspended automation.
