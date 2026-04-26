# AabSettings DataStore Schema

Typed profile schema lives in `AabSettings` and stores migrated Tasker settings in app-private DataStore.

## `%AAB_*` variable map

| Tasker variable | Key | Type | Default | Validation |
|---|---|---|---|---|
| `%AAB_Service` | `serviceEnabled` | Boolean | `true` | must be true/false |
| `%AAB_DetectOverrides` | `detectOverrides` | Boolean | `false` | must be true/false |
| `%AAB_MinBright` | `minBrightness` | Int | `10` | `1..255` |
| `%AAB_MaxBright` | `maxBrightness` | Int | `255` | `1..255`, `>= minBrightness` |
| `%AAB_Offset` | `offset` | Int | `0` | `-255..255` |
| `%AAB_Scale` | `scale` | Int | `1` | `1..10` |
| `%AAB_Zone1End` | `zone1End` | Int | `35` | `1..20000` |
| `%AAB_Zone2End` | `zone2End` | Int | `10000` | `1..100000`, `>= zone1End` |
| `%AAB_Form1A` | `form1A` | Int | `5` | `1..20` |
| `%AAB_Form2B` | `form2B` | Float | `8.8` | `0.1..30.0` |
| `%AAB_Form2C` | `form2C` | Int | `18` | `1..50` |
| `%AAB_DimmingEnabled` | `dimmingEnabled` | Boolean | `false` | must be true/false |
| `%AAB_DimmingStrength` | `dimmingStrength` | Int | `25` | `0..100` |
| `%AAB_DimmingExponent` | `dimmingExponent` | Float | `2.5` | `0.5..5.0` |
| `%AAB_DimmingThreshold` | `dimmingThreshold` | Int | `15` | `0..100` |
| `%AAB_DimSpread` | `dimSpread` | Int | `100` | `1..300` |
| `%AAB_PWMSensitive` | `pwmSensitive` | Boolean | `false` | must be true/false |
| `%AAB_PWMExp` | `pwmExponent` | Float | `0.8` | `0.1..3.0` |
| `%AAB_DefaultThrottle` | `throttleDefaultMs` | Long | `1000` | `100..60000` |
| `%AAB_MinWait` | `minWaitMs` | Int | `25` | `1..5000` |
| `%AAB_MaxWait` | `maxWaitMs` | Int | `65` | `1..5000`, `>= minWaitMs` |
| `%AAB_DeltaFactor` | `deltaFactor` | Float | `1.8` | `0.1..10.0` |
| `%AAB_ThreshBright` | `thresholdBright` | Float | `0.08` | `0.0..1.0` |
| `%AAB_ThreshDark` | `thresholdDark` | Float | `0.3` | `0.0..1.0` |
| `%AAB_ThreshDim` | `thresholdDim` | Float | `0.25` | `0.0..1.0` |
| `%AAB_ThreshDynamic` | `thresholdDynamic` | Int | `5` | `1..20` |
| `%AAB_ThreshSteepness` | `thresholdSteepness` | Float | `2.1` | `0.1..10.0` |
| `%AAB_ScalingUse` | `scalingEnabled` | Boolean | `false` | must be true/false |
| `%AAB_ScaleSpread` | `scaleSpread` | Int | `15` | `1..100` |
| `%AAB_ScaleSteepness` | `scaleSteepness` | Int | `6` | `1..20` |
| `%AAB_ScaleTaperMidpoint` | `scaleTaperMidpoint` | Int | `190` | `1..255` |
| `%AAB_ScaleTaperSteepness` | `scaleTaperSteepness` | Float | `0.075` | `0.001..1.0` |
| `%AAB_ScaleTransitionFactor` | `scaleTransitionFactor` | Float | `0.1` | `0.0..1.0` |
| `%AAB_TrustUnreliable` | `trustUnreliableSensor` | Boolean | `false` | must be true/false |
| `%AAB_QSUse` | `quickSettingsEnabled` | Boolean | `false` | must be true/false |
| `%AAB_NotifyUse` | `notificationsEnabled` | Boolean | `true` | must be true/false |
| `%AAB_Debug` | `debugLevel` | Int | `0` | `0..5` |

Legacy migration parser accepts Tasker-style lines:

```
%AAB_Service=On
%AAB_MinBright=12
%AAB_MaxBright=255
```

Then normalizes and validates into native `AabSettings`.
