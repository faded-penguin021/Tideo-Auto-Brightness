# Variable Contracts

Derived from Tasker variable reads/writes in `Advanced_Auto_Brightness_V3.3.prj_4.xml`.

| Variable | Type | Owner Task | Lifetime | Default | Writers | Readers |
|---|---|---|---|---|---|---|
| `%AAB_AnimSteps` | integer | Initialize AAB Defaults | global_tasker_variable | 20 | Initialize AAB Defaults | Calculate Animation (Java) V2, Initialize AAB Defaults, Reset Brightness and State, Reset Throttle |
| `%AAB_ChartJs` | variable-ref | _ChartJsChunks | global_tasker_variable | None | _ChartJsChunks | _CalibratePowerDraw, _ChartJsChunks, _GenerateAlphaGraph (Java), _GenerateCircadianDimmingGraph (Java) |
| `%AAB_ContextJSONCache` | unknown | _ResetContextCacheDaily | global_tasker_variable | None |  | _ResetContextCacheDaily |
| `%AAB_ContextLocMode` | integer | _ContextLocModeRead | global_tasker_variable | -1 | _ContextLocModeRead | _ContextLocModeRead |
| `%AAB_ContextOverride` | boolean/enum | _ContextResume | global_tasker_variable | false | _ContextResume, _ProfileManager | _ContextResume, _ProfileManager |
| `%AAB_CurrentActiveProfile` | variable-ref | _EvaluateContexts V2 | global_tasker_variable | None | _EvaluateContexts V2, _ProfileManager | _EvaluateContexts V2, _ProfileManager |
| `%AAB_CurrentBright` | variable-ref | Map Lux to Brightness (Java) V2 | global_tasker_variable | None | Map Lux to Brightness (Java) V2 | Calculate Super Dimming (Privileged) V4, Calculate Super Dimming (Unprivileged) V3, Map Lux to Brightness (Java) V2, Smooth Brightness Transition V5 (Java) |
| `%AAB_CycleStart` | variable-ref | Set Initial Brightness (Java) V3 | global_tasker_variable | None | Set Initial Brightness (Java) V3 | Evaluate Light Change (Java) V2, Set Initial Brightness (Java) V3, Smooth Brightness Transition V5 (Java), Smooth DC-Like Brightness Transition V5 (Java) |
| `%AAB_CycleTime` | unknown | Smooth Brightness Transition V5 (Java) | global_tasker_variable | None |  | Smooth Brightness Transition V5 (Java), Smooth DC-Like Brightness Transition V5 (Java) |
| `%AAB_CycleTotal` | variable-ref | Evaluate Light Change (Java) V2 | global_tasker_variable | None | Evaluate Light Change (Java) V2 | Evaluate Light Change (Java) V2 |
| `%AAB_Date` | variable-ref | _ExperimentSetDate | global_tasker_variable | None | _ExperimentSetDate | _ExperimentClearDate, _ExperimentSetDate |
| `%AAB_Debug` | integer | Initialize AAB Defaults | global_tasker_variable | 0 | Initialize AAB Defaults, _SetDebugLevel | Initialize AAB Defaults, _SetDebugLevel |
| `%AAB_DefaultThrottle` | variable-ref | Reset Throttle | global_tasker_variable | 1000 | Reset Throttle | Reset Throttle, Set Initial Brightness (Java) V3, _SaveButtonMisc |
| `%AAB_DeltaFactor` | float | Initialize AAB Defaults | global_tasker_variable | 1.8 | Initialize AAB Defaults | Initialize AAB Defaults, Lux Smoothing (Java), _MiscScene, _ReactivityScene |
| `%AAB_DetectOverrides` | boolean/enum | Initialize AAB Defaults | global_tasker_variable | Off | Initialize AAB Defaults, _LoadedOverrideToggle, _OverrideToggle | Initialize AAB Defaults, _LoadedOverrideToggle, _OverrideToggle |
| `%AAB_DimDynamic` | unknown | Calculate Super Dimming (Privileged) V4 | global_tasker_variable | None |  | Calculate Super Dimming (Privileged) V4, Calculate Super Dimming (Unprivileged) V3 |
| `%AAB_DimSpread` | integer | Initialize AAB Defaults | global_tasker_variable | 100 | Initialize AAB Defaults | Initialize AAB Defaults, _SaveButtonDimming, _SuperDimmingScene, unnamed_500 |
| `%AAB_DimmingCurrent` | variable-ref | Apply Dimming (Privileged) | global_tasker_variable | 0 | Apply Dimming (Privileged), Apply Dimming (Unprivileged), Calculate Super Dimming (Unprivileged) V3, Dimming Decider | Apply Dimming (Privileged), Apply Dimming (Unprivileged), Calculate Super Dimming (Privileged) V4, Calculate Super Dimming (Unprivileged) V3 |
| `%AAB_DimmingDS` | integer | Apply Dimming (Privileged) | global_tasker_variable | 0 | Apply Dimming (Privileged), Apply Dimming (Unprivileged), Calculate Super Dimming (Unprivileged) V3, Dimming Decider | Apply Dimming (Privileged), Apply Dimming (Unprivileged), Calculate Super Dimming (Privileged) V4, Calculate Super Dimming (Unprivileged) V3 |
| `%AAB_DimmingEnabled` | boolean/enum | Initialize AAB Defaults | global_tasker_variable | false | Initialize AAB Defaults, _DimmingUIToggle, _PWMToggle | Initialize AAB Defaults, _DimmingUIToggle, _PWMToggle |
| `%AAB_DimmingExponent` | float | Initialize AAB Defaults | global_tasker_variable | 2.5 | Initialize AAB Defaults | Calculate Super Dimming (Privileged) V4, Calculate Super Dimming (Unprivileged) V3, Initialize AAB Defaults, _SaveButtonDimming |
| `%AAB_DimmingStatus` | integer | ARGB To Hex | global_tasker_variable | 0 | ARGB To Hex, Apply Dimming (Privileged), Apply Dimming (Unprivileged), Disable Super Dimming (Privileged) | ARGB To Hex, Apply Dimming (Privileged), Apply Dimming (Unprivileged), Disable Super Dimming (Privileged) |
| `%AAB_DimmingStrength` | integer | Initialize AAB Defaults | global_tasker_variable | 25 | Initialize AAB Defaults | Calculate Super Dimming (Privileged) V4, Calculate Super Dimming (Unprivileged) V3, Initialize AAB Defaults, _SaveButtonDimming |
| `%AAB_DimmingStrengthCurr` | variable-ref | Apply Dimming (Privileged) | global_tasker_variable | None | Apply Dimming (Privileged), Calculate Super Dimming (Unprivileged) V3 | Apply Dimming (Privileged), Calculate Super Dimming (Privileged) V4, Calculate Super Dimming (Unprivileged) V3 |
| `%AAB_DimmingThreshold` | integer | Initialize AAB Defaults | global_tasker_variable | 15 | Initialize AAB Defaults | Calculate Super Dimming (Privileged) V4, Calculate Super Dimming (Unprivileged) V3, Initialize AAB Defaults, Smooth DC-Like Brightness Transition V5 (Java) |
| `%AAB_EveningDuration` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version |
| `%AAB_EveningEnd` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version |
| `%AAB_EveningStart` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version |
| `%AAB_Form1A` | integer | Initialize AAB Defaults | global_tasker_variable | 5 | Initialize AAB Defaults | Advanced Auto Brightness, Initialize AAB Defaults, Map Lux to Brightness (Java) V2, _SaveButtonGeneral |
| `%AAB_Form2A` | variable-ref | Initialize AAB Defaults | global_tasker_variable | None | Initialize AAB Defaults | Advanced Auto Brightness, Initialize AAB Defaults, Map Lux to Brightness (Java) V2, _SaveButtonGeneral |
| `%AAB_Form2B` | float | Initialize AAB Defaults | global_tasker_variable | 8.8 | Initialize AAB Defaults | Advanced Auto Brightness, Initialize AAB Defaults, Map Lux to Brightness (Java) V2, _SaveButtonGeneral |
| `%AAB_Form2C` | integer | Initialize AAB Defaults | global_tasker_variable | 18 | Initialize AAB Defaults | Advanced Auto Brightness, Initialize AAB Defaults, Map Lux to Brightness (Java) V2, _RedInvalidFormulae |
| `%AAB_Form2D` | variable-ref | Initialize AAB Defaults | global_tasker_variable | None | Initialize AAB Defaults, _SaveButtonGeneral | Advanced Auto Brightness, Initialize AAB Defaults, Map Lux to Brightness (Java) V2, _SaveButtonGeneral |
| `%AAB_Form3A` | string | Initialize AAB Defaults | global_tasker_variable | None | Initialize AAB Defaults, _SaveButtonMisc | Advanced Auto Brightness, Initialize AAB Defaults, Map Lux to Brightness (Java) V2, _SaveButtonGeneral |
| `%AAB_HTML_Graph` | variable-ref | _GenerateGraph (Java) | global_tasker_variable | None | _GenerateGraph (Java) | _DeleteOverridePoint, _GenerateGraph (Java) |
| `%AAB_HTML_Graph2` | variable-ref | _GenerateReactivityGraph (Java) | global_tasker_variable | None | _GenerateReactivityGraph (Java) | _GenerateReactivityGraph (Java) |
| `%AAB_HTML_Graph3` | variable-ref | _GenerateAlphaGraph (Java) | global_tasker_variable | None | _GenerateAlphaGraph (Java) | _GenerateAlphaGraph (Java) |
| `%AAB_HTML_Graph4` | variable-ref | _GenerateCircadianGraph V8 (Java) | global_tasker_variable | None | _GenerateCircadianGraph V8 (Java) | _GenerateCircadianGraph V8 (Java) |
| `%AAB_HTML_Graph5` | variable-ref | _GenerateCompressionGraph (Java) | global_tasker_variable | None | _GenerateCompressionGraph (Java) | _GenerateCompressionGraph (Java) |
| `%AAB_HTML_Graph6` | variable-ref | _GenerateDimmingCurveGraph (Java) | global_tasker_variable | None | _GenerateDimmingCurveGraph (Java) | _GenerateDimmingCurveGraph (Java) |
| `%AAB_HTML_Graph7` | variable-ref | _GenerateCircadianDimmingGraph (Java) | global_tasker_variable | None | _GenerateCircadianDimmingGraph (Java) | _GenerateCircadianDimmingGraph (Java) |
| `%AAB_HTML_Graph8` | variable-ref | _CalibratePowerDraw | global_tasker_variable | None | _CalibratePowerDraw | _CalibratePowerDraw |
| `%AAB_HexOverlay` | string | ARGB To Hex | global_tasker_variable | None | ARGB To Hex | ARGB To Hex, Disable Super Dimming (Unprivileged) |
| `%AAB_Initializing` | boolean/enum | Set Initial Brightness (Java) V3 | global_tasker_variable | true | Set Initial Brightness (Java) V3 | Set Initial Brightness (Java) V3 |
| `%AAB_JavaDialogResponse` | unknown | _DeleteOverridePoint | global_tasker_variable | None |  | _DeleteOverridePoint |
| `%AAB_LastAnimation` | unknown | Smooth Brightness Transition V5 (Java) | global_tasker_variable | None |  | Smooth Brightness Transition V5 (Java), Smooth DC-Like Brightness Transition V5 (Java) |
| `%AAB_LastRawLux` | variable-ref | Set Initial Brightness (Java) V3 | global_tasker_variable | None | Set Initial Brightness (Java) V3 | Process Sensor Event (Java), Set Initial Brightness (Java) V3, Set Thresholds (Java) |
| `%AAB_LastSensorAccuracy` | variable-ref | Set Initial Brightness (Java) V3 | global_tasker_variable | None | Set Initial Brightness (Java) V3 | Set Initial Brightness (Java) V3 |
| `%AAB_Latitude` | variable-ref | _ExperimentSetDate | global_tasker_variable | None | _ExperimentSetDate | _ExperimentClearDate, _ExperimentSetDate |
| `%AAB_LocnBackOff` | unknown | _ContextF5NetLoc V8 | global_tasker_variable | None |  | _ContextF5NetLoc V8 |
| `%AAB_LocnLog` | unknown | _ContextLocnListener V4 | global_tasker_variable | None |  | _ContextLocnListener V4 |
| `%AAB_Longitude` | variable-ref | _ExperimentSetDate | global_tasker_variable | None | _ExperimentSetDate | _ExperimentClearDate, _ExperimentSetDate |
| `%AAB_MainLoop` | integer | Evaluate Light Change (Java) V2 | global_tasker_variable | 0 | Evaluate Light Change (Java) V2, Process Sensor Event (Java) | Evaluate Light Change (Java) V2, Process Sensor Event (Java), Reset Brightness and State |
| `%AAB_Manual_Override` | boolean/enum | Manual Override | global_tasker_variable | true | Manual Override, _PanicButton | Manual Override, Set Initial Brightness (Java) V3, _ContextResume, _PanicButton |
| `%AAB_MaxBright` | integer | Initialize AAB Defaults | global_tasker_variable | 255 | Initialize AAB Defaults | Dynamic Range Compressed Scale (Java) V2, Initialize AAB Defaults, Map Lux to Brightness (Java) V2, _GenerateCompressionGraph (Java) |
| `%AAB_MaxWait` | integer | Initialize AAB Defaults | global_tasker_variable | 65 | Initialize AAB Defaults | Calculate Animation (Java) V2, Initialize AAB Defaults, Reset Throttle, Set Initial Brightness (Java) V3 |
| `%AAB_MinBright` | integer | Initialize AAB Defaults | global_tasker_variable | 10 | Initialize AAB Defaults | Calculate Super Dimming (Privileged) V4, Calculate Super Dimming (Unprivileged) V3, Dynamic Range Compressed Scale (Java) V2, Initialize AAB Defaults |
| `%AAB_MinThrottle` | unknown | _MiscScene | global_tasker_variable | None |  | _MiscScene, unnamed_738 |
| `%AAB_MinWait` | integer | Initialize AAB Defaults | global_tasker_variable | 25 | Initialize AAB Defaults | Calculate Animation (Java) V2, Initialize AAB Defaults, Reset Brightness and State, _MiscScene |
| `%AAB_MorningDuration` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version |
| `%AAB_MorningEnd` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version |
| `%AAB_MorningStart` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version |
| `%AAB_NetLocation` | unknown | Reset Brightness and State | global_tasker_variable | None |  | Reset Brightness and State, _EvaluateContexts V2 |
| `%AAB_NotifyUse` | boolean/enum | _NotifyToggle | global_tasker_variable | true | _NotifyToggle, _Updates | _NotifyToggle, _Updates |
| `%AAB_NowSS` | variable-ref | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None | Dynamic Scale V13 (Java) App Version | Dynamic Scale V13 (Java) App Version |
| `%AAB_Offset` | integer | Initialize AAB Defaults | global_tasker_variable | 0 | Initialize AAB Defaults | Dynamic Range Compressed Scale (Java) V2, Initialize AAB Defaults, Map Lux to Brightness (Java) V2, _MiscScene |
| `%AAB_Overrides` | unknown | Process Overrides | global_tasker_variable | None |  | Process Overrides, _DeleteOverridePoint, _GenerateGraph (Java), unnamed_724 |
| `%AAB_PWMExp` | float | Initialize AAB Defaults | global_tasker_variable | 0.8 | Initialize AAB Defaults, _Updates | Initialize AAB Defaults, Software Dimming V2, _SaveButtonDimming, _SuperDimmingScene |
| `%AAB_PWMSensitive` | boolean/enum | _DimmingUIToggle | global_tasker_variable | false | _DimmingUIToggle, _PWMToggle, _Updates | _DimmingUIToggle, _PWMToggle, _Updates |
| `%AAB_Package` | unknown | _LearnWriteSecure | global_tasker_variable | None |  | _LearnWriteSecure |
| `%AAB_PermGranted` | unknown | _AskPermissionsV7 | global_tasker_variable | None |  | _AskPermissionsV7, _Updates |
| `%AAB_PolarState` | boolean/enum | Dynamic Scale V13 (Java) App Version | global_tasker_variable | true | Dynamic Scale V13 (Java) App Version | Dynamic Scale V13 (Java) App Version |
| `%AAB_PrevBright` | variable-ref | Smooth DC-Like Brightness Transition V5 (Java) | global_tasker_variable | None | Smooth DC-Like Brightness Transition V5 (Java) | Map Lux to Brightness (Java) V2, Reset Brightness and State, Smooth DC-Like Brightness Transition V5 (Java) |
| `%AAB_Privilege` | string | _PrivilegeDetection V5 (Java) | global_tasker_variable | None | _PrivilegeDetection V5 (Java) | _PrivilegeDetection V5 (Java), _SuperDimmingLongTap, unnamed_492 |
| `%AAB_ProfileUser` | variable-ref | _ProfileManager | global_tasker_variable | None | _ProfileManager | _ProfileManager |
| `%AAB_Proximity` | string | Detect Proximity | global_tasker_variable | near | Detect Proximity | Detect Proximity |
| `%AAB_QSUse` | boolean/enum | Initialize AAB Defaults | global_tasker_variable | false | Initialize AAB Defaults, _QSExperimentUIToggle | Initialize AAB Defaults, _QSExperimentUIToggle |
| `%AAB_ResumeTapped` | boolean/enum | Manual Override | global_tasker_variable | Off | Manual Override, Resume After Override | Manual Override, Resume After Override |
| `%AAB_Scale` | integer | Initialize AAB Defaults | global_tasker_variable | 1 | Initialize AAB Defaults | Initialize AAB Defaults, Map Lux to Brightness (Java) V2, _MiscScene, _SaveButtonMisc |
| `%AAB_ScaleDynamic` | unknown | Dynamic Range Compressed Scale (Java) V2 | global_tasker_variable | None |  | Dynamic Range Compressed Scale (Java) V2, Dynamic Scale V13 (Java) App Version |
| `%AAB_ScaleDynamicCompress` | unknown | Process Overrides | global_tasker_variable | None |  | Process Overrides |
| `%AAB_ScaleSpread` | integer | Initialize AAB Defaults | global_tasker_variable | 15 | Initialize AAB Defaults | Initialize AAB Defaults, _ExperimentScene, _SaveButtonExperiment, unnamed_675 |
| `%AAB_ScaleSteepness` | integer | Initialize AAB Defaults | global_tasker_variable | 6 | Initialize AAB Defaults | Initialize AAB Defaults, _ExperimentScene, _SaveButtonExperiment, unnamed_675 |
| `%AAB_ScaleTaperMidpoint` | integer | Initialize AAB Defaults | global_tasker_variable | 190 | Initialize AAB Defaults | Dynamic Range Compressed Scale (Java) V2, Initialize AAB Defaults, _ExperimentScene, _SaveButtonExperiment |
| `%AAB_ScaleTaperSteepness` | float | Initialize AAB Defaults | global_tasker_variable | 0.075 | Initialize AAB Defaults | Dynamic Range Compressed Scale (Java) V2, Initialize AAB Defaults, _ExperimentScene, _SaveButtonExperiment |
| `%AAB_ScaleTransitionFactor` | float | Initialize AAB Defaults | global_tasker_variable | 0.1 | Initialize AAB Defaults | Dynamic Scale V13 (Java) App Version, Initialize AAB Defaults, _ExperimentScene, _SaveButtonExperiment |
| `%AAB_ScalingUse` | boolean/enum | Initialize AAB Defaults | global_tasker_variable | false | Initialize AAB Defaults, _ExperimentUIToggle | Initialize AAB Defaults, _ExperimentUIToggle |
| `%AAB_Scenebg` | string | Advanced Auto Brightness | global_tasker_variable | <!DOCTYPE html>
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
</html> | Advanced Auto Brightness | Advanced Auto Brightness |
| `%AAB_Service` | boolean/enum | Initialize AAB Defaults | global_tasker_variable | On | Initialize AAB Defaults, Resume After Override, _ContextResume, _PanicButton | Initialize AAB Defaults, Resume After Override, _ContextResume, _PanicButton |
| `%AAB_SettingsBGone` | string | _Updates | global_tasker_variable | None | _Updates | _Updates |
| `%AAB_SettingsBGtwo` | string | _Updates | global_tasker_variable | );">
            <span></span>
            <span></span>
            <span></span>
        </div>

        <h1>Advanced Auto Brightness</h1>
    </div>
</body>
</html> | _Updates | _Updates |
| `%AAB_SetupComplete` | integer | Advanced Auto Brightness | global_tasker_variable | 1 | Advanced Auto Brightness, unnamed_396, unnamed_500, unnamed_675 | Advanced Auto Brightness, unnamed_396, unnamed_500, unnamed_675 |
| `%AAB_SetupTitle` | string | Initialize AAB Defaults | global_tasker_variable | Advanced Auto Brightness Setup | Initialize AAB Defaults | Initialize AAB Defaults, Set Initial Brightness (Java) V3 |
| `%AAB_SunLastDate` | variable-ref | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None | Dynamic Scale V13 (Java) App Version | Dynamic Scale V13 (Java) App Version, _ExperimentUIToggle, _GenerateCircadianDimmingGraph (Java), _GenerateCircadianGraph V8 (Java) |
| `%AAB_Sundawn` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version |
| `%AAB_Sundusk` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version |
| `%AAB_Sunlightduration` | variable-ref | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None | Dynamic Scale V13 (Java) App Version | Dynamic Scale V13 (Java) App Version |
| `%AAB_Sunnoon` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version |
| `%AAB_Sunrise` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version, _GenerateCircadianDimmingGraph (Java), _GenerateCircadianGraph V8 (Java) |
| `%AAB_Sunset` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version |
| `%AAB_Test` | variable-ref | _SuggestCurveParameters V23 (Hybrid) | global_tasker_variable | None | _SuggestCurveParameters V23 (Hybrid) | _SuggestCurveParameters V23 (Hybrid) |
| `%AAB_ThreshAbsHigh` | unknown | Evaluate Light Change (Java) V2 | global_tasker_variable | None |  | Evaluate Light Change (Java) V2 |
| `%AAB_ThreshAbsLow` | unknown | Evaluate Light Change (Java) V2 | global_tasker_variable | None |  | Evaluate Light Change (Java) V2 |
| `%AAB_ThreshBright` | float | Initialize AAB Defaults | global_tasker_variable | 0.08 | Initialize AAB Defaults | Evaluate Light Change (Java) V2, Initialize AAB Defaults, _ReactivityScene, _SaveButtonReactivity |
| `%AAB_ThreshDark` | float | Initialize AAB Defaults | global_tasker_variable | 0.3 | Initialize AAB Defaults | Evaluate Light Change (Java) V2, Initialize AAB Defaults, _ReactivityScene, _SaveButtonReactivity |
| `%AAB_ThreshDim` | float | Initialize AAB Defaults | global_tasker_variable | 0.25 | Initialize AAB Defaults | Evaluate Light Change (Java) V2, Initialize AAB Defaults, _ReactivityScene, _SaveButtonReactivity |
| `%AAB_ThreshDynamic` | integer | Initialize AAB Defaults | global_tasker_variable | 5 | Initialize AAB Defaults | Evaluate Light Change (Java) V2, Initialize AAB Defaults, Lux Smoothing (Java) |
| `%AAB_ThreshMidpoint` | string | Initialize AAB Defaults | global_tasker_variable | None | Initialize AAB Defaults | Evaluate Light Change (Java) V2, Initialize AAB Defaults, _ReactivityScene, _SaveButtonReactivity |
| `%AAB_ThreshSteepness` | float | Initialize AAB Defaults | global_tasker_variable | 2.1 | Initialize AAB Defaults | Evaluate Light Change (Java) V2, Initialize AAB Defaults, _ReactivityScene, _SaveButtonReactivity |
| `%AAB_Throttle` | variable-ref | Initialize AAB Defaults | global_tasker_variable | None | Initialize AAB Defaults, Reset Brightness and State, Reset Throttle, Set Initial Brightness (Java) V3 | Evaluate Light Change (Java) V2, Initialize AAB Defaults, Reset Brightness and State, Reset Throttle |
| `%AAB_TrustUnreliable` | boolean/enum | Initialize AAB Defaults | global_tasker_variable | Off | Initialize AAB Defaults, _ReactivityToggle | Initialize AAB Defaults, _ReactivityScene, _ReactivityToggle, unnamed_710 |
| `%AAB_Version` | float | _Updates | global_tasker_variable | 3.3 | _Updates | _Updates |
| `%AAB_Zone1End` | integer | Initialize AAB Defaults | global_tasker_variable | 35 | Initialize AAB Defaults | Advanced Auto Brightness, Evaluate Light Change (Java) V2, Initialize AAB Defaults, Lux Smoothing (Java) |
| `%AAB_Zone2End` | integer | Initialize AAB Defaults | global_tasker_variable | 10000 | Initialize AAB Defaults | Advanced Auto Brightness, Initialize AAB Defaults, _SaveButtonGeneral, _SaveButtonMisc |
| `%AAB_calc_dawn` | variable-ref | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None | Dynamic Scale V13 (Java) App Version | Dynamic Scale V13 (Java) App Version |
| `%AAB_calc_dusk` | variable-ref | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None | Dynamic Scale V13 (Java) App Version | Dynamic Scale V13 (Java) App Version |
| `%AAB_calc_noon` | variable-ref | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None | Dynamic Scale V13 (Java) App Version | Dynamic Scale V13 (Java) App Version |
| `%AAB_calc_sunrise` | unknown | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None |  | Dynamic Scale V13 (Java) App Version |
| `%AAB_calc_sunset` | variable-ref | Dynamic Scale V13 (Java) App Version | global_tasker_variable | None | Dynamic Scale V13 (Java) App Version | Dynamic Scale V13 (Java) App Version |
| `%app_list_json` | unknown | _AppPicker | global_tasker_variable | None |  | _AppPicker |
| `%as_accuracy` | unknown | Process Sensor Event (Java) | global_tasker_variable | None |  | Process Sensor Event (Java), Set Initial Brightness (Java) V3 |
| `%as_values1` | unknown | Process Sensor Event (Java) | global_tasker_variable | None |  | Process Sensor Event (Java), Set Initial Brightness (Java) V3 |
