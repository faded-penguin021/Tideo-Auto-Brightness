# pipeline_spec.md — end-to-end core pipeline (S1)

The authoritative prose+pseudocode flow of the Tasker auto-brightness engine, synthesized from the
S1 extractions (`tasks/*.md`, `java/*.java`, `profiles.md`). **Later segments consume THIS, not the
XML.** Provenance is given as `taskNNN actK (Lxxxxx)`; Java semantics are in the `java/` files.

Maths/rounding to preserve (S4/S5): `Math.round(x*1000)/1000` (3-decimal), `BigDecimal.setScale(n,
ROUND_HALF_UP)`, string-formatted returns (`a + "," + b`), int truncation. See per-block Java.

---

## 0. Actors & triggers (profiles → enter tasks)

| Trigger | Profile | Enter task | Role |
|---|---|---|---|
| Light sensor event (gated) | prof760 (pri 7) | task554 | **main loop tick** |
| Display ON | prof761 (pri 7) | task618 | wake → initial brightness |
| Display OFF | prof753 (pri 3) | task585 | hibernate / reset |
| Setting `screen_brightness` changed | prof755 (pri 10) | task567 | **override detect** |
| State (paused, screen on) | prof756 | task567 | repost paused notif |
| Time periodic | prof757 | task584 | repost running notif |
| Time windows (dawn/dusk) | prof758 | task90 | dynamic scale recompute |
| Proximity | prof759 (pri 4) | task545 | set %AAB_Proximity near/far → LuxAlpha damp ×0.1 (no pause) |
| Throttle drift | prof754 | task566 | throttle reinit |
| Panic event | prof769 (pri 15) | task528 | emergency reset |

prof760's gate (accuracy-trust + absolute dead-band + main-loop) is in `profiles.md`; it runs **before**
task554 — the absolute-threshold gate is profile-level, not in the engine.

---

## 1. Main loop: sensor event → brightness write

### 1a. task554 Process Sensor Event (`java/task554_1`)
1. `%AAB_MainLoop = On` (act0).
2. Java (act1): `AAB_CycleStart = TIMEMS`; clean `%as_values1` (strip `[]`), parse to double,
   `AAB_LastRawLux = round3(lux)`; `AAB_LastSensorAccuracy = %as_accuracy`. Writes via
   `tasker.setVariable` (no arg1). Re-publishes cleaned `%as_values1`.
3. Perform **task544 Evaluate Light Change** with `par1 = %as_values1` (act2).

### 1b. task544 Evaluate Light Change (`java/task544_1`, + 41 actions)
Order is by `actN` (Tasker execution order), NOT document order. Key sequence:
1. **Guard:** Stop if `%par1` unset (act0). Stop if a relevant task already running
   (`%TRUN` matches *Map Lux To Brightness* / *Smooth Brightness Transition* / *Process Sensor Event*)
   — re-entrancy lock (act1).
2. **Throttle gate** (act2–9): `now=%TIMEMS`, `min_interval=%AAB_Throttle`; if `%LastAAB` set and
   `now-%LastAAB < min_interval` → `%AAB_MainLoop=0`, **Stop** (drop tick).
3. **First-run init** (act10–17): if `%SmoothedLux` unset ∧ `%AutoBrightRunning≠1` →
   `SmoothedLux=par1`, `LuxAlpha=1`, `LastAAB=now`, seed thresholds, Perform **Map Lux to Brightness**
   (pri+1) with `par1=SmoothedLux, par2=LuxAlpha`, **Stop**.
4. **Dynamic-threshold Java** (act18, `java/task544_1`): computes
   `relative_change = round3(|par1−SmoothedLux| / (SmoothedLux+1))` and a **sigmoid dynamic threshold**
   `thresh_sig = ThreshDim + (ThreshBright−ThreshDim)/(1+exp(−ThreshSteepness·(log10(SmoothedLux+1)−ThreshMidpoint)))`
   (plus a dark-region branch using `ThreshDark`). Outputs `%relative_change`, `%dynamic_threshold`.
5. **Dead-band** (act19–24): if `relative_change < dynamic_threshold` → Perform **Set Thresholds**,
   clear `%AAB_CycleStart`, `MainLoop=0`, **Stop** (change too small).
6. **Smoothing** (act25–27): Perform **task535 Lux Smoothing** `par1=par1, par2=SmoothedLux` →
   `%lux_results = "smoothed,alpha,delta"`; split → `%new_smoothed_lux`, `%SmoothedLux`, `%LuxAlpha`.
7. **Proximity damp** (act28–29): if `%AAB_Proximity = near` → `LuxAlpha = lux_results2 * 0.1`.
8. → Perform **task661 Map Lux to Brightness**.

### 1c. task535 Lux Smoothing (`java/task535_1`)
Exponential smoothing with hysteresis:
- `lux_delta = round3(|par1−par2| / (par2+1))`
- `effective_delta = round3(lux_delta − ThreshDynamic/100)`
- `lux_alpha = round3(1 − exp(−DeltaFactor·effective_delta))`
- `new_smoothed = par1·alpha + par2·(1−alpha)`
- **string format:** if `new_smoothed < Zone1End` → `BigDecimal.setScale(2, HALF_UP)` else `setScale(0, HALF_UP)`
- returns `"<final_lux_string>,<lux_alpha>,<lux_delta>"`. (par1=raw lux, par2=prior smoothed.)

### 1d. task661 Map Lux to Brightness (`tasks/task661_*`, 52 actions — **runtime curve math, D-002**)
1. `%AutoBrightRunning=1` (act0); `%smoothed_lux=par1`, `%lux_alpha=par2`.
2. **3-zone curve** (act3–9):
   - zone1 `smoothed_lux < Zone1End`: `mapped = Form1A·sqrt(par1)`
   - zone2 `smoothed_lux < Zone2End`: `mapped = Form2A + Form2B·((par1−Form2C)^0.33 − (Form2D−Form2C)^0.33)`
   - zone3 else: `mapped = MaxBright − (Form3A/par1)·MaxBright`
   (Form2A/Form2D/Form3A are derived — §3. Cross-check vs task663 Java plot copy, never port from it.)
3. **Scale/offset** (act10–15): if `%AAB_ScalingUse = true` → Perform **task548 Dynamic Range
   Compressed Scale** → `calculated = dr_results`; else `calculated = mapped·Scale + Offset` [DoMaths].
4. **Clamp** (act16–21): `calculated = clamp(calculated, MinBright, MaxBright)`.
5. **Super-dimming branch** (act22–37):
   - if `PWMSensitive = true ∧ calculated < DimmingThreshold` → Perform **task700 Software Dimming**
     → `%final_dim`; Perform **task543 Calculate Animation** (`par1=lux_alpha`) → `%calculate_results`;
     Perform **task698 Smooth DC-Like Brightness Transition** (`par1="calculated,final_dim"`,
     `par2=calculate_results`); **Stop**.
   - else-if `PWMSensitive ∧ DimmingStatus=1 ∧ calculated > DimmingThreshold` → Perform **Disable Super
     Dimming** (Privileged if `%AAB_Privilege≠None`, else Unprivileged); reset `DimmingCurrent=0`,
     `DimmingDS=0`, clear `PrevBright`, `DimmingStatus=0`.
6. **Debug readout** (act38–43): set `%AAB_CurrentBright` (1-dp below DimmingThreshold for accuracy).
7. Perform **Dimming Decider** (act44); **Set Display Brightness `%calculated_brightness`** (act45, code810);
   `%AutoBrightRunning=0` unless caller is *Set Initial Brightness* (act46); **Stop** (act47).
8. **Non-dimming path** (act49+): Perform **task543 Calculate Animation** (`par1=lux_alpha`) then
   **task696 Smooth Brightness Transition** (`par1=calculated`, `par2=calculate_results`).

### 1e. task548 Dynamic Range Compressed Scale (`java/task548_1`)
Applies `%AAB_ScaleDynamic`/`%AAB_ScaleDynamicCompress` compression to `mapped` brightness when dynamic
scaling is active (circadian multiplier from task90). Returns compressed brightness. Math in Java file.
**Why it exists (owner, S3.5):** it compresses the circadian *multiplier* toward the top of the
brightness range — without it a high base (e.g. 240) with a >1 multiplier (e.g. 1.15) pins at 255 for
too long, and a sub-1 multiplier (e.g. 0.85) could never reach 255 no matter how bright the ambient
light. Rationale only — behavior is ported from the Java unchanged.

### 1f. task543 Calculate Animation (`java/task543_1`) → returns `"%loops,%wait"`
Computes step count `loops` and per-step `wait` (ms) for the transition, from `lux_alpha` and the
`AnimSteps`/`MinWait`/`MaxWait`/`Throttle` settings. Output consumed by 696/698.

### 1g. task696 / task698 Smooth (DC-Like) Brightness Transition (`java/task696_1`, `task698_1`)
Per-frame animated write **with read-back override detection** (Tasker parity):
- Read `startBrightness = Settings.System.getInt(SCREEN_BRIGHTNESS)`; parse `par1` target, `par2="loops,wait"`
  (fallback `"20,30"`); read `DimmingThreshold` (fallback 5), `DimmingEnabled`, `DetectOverrides`.
- Compute `minTarget/maxTarget` band around start↔target; step `loops` times writing intermediate
  brightness, `Thread.sleep(wait)` between frames; **on each frame re-read the system value and, if it
  drifted outside the expected band (and `DetectOverrides=On`), abort and signal manual override**
  (→ prof755/task567). 698 is the DC-like (super-dimming-aware) variant taking `"brightness,dim"`.
- This is the **suppress-echo contract**: our own writes are expected; an unexpected external write = override.

---

## 2. Override detect / pause / resume state machine

State vars: `%AAB_Manual_Override` (true=paused), `%AAB_ResumeTapped`, `%AAB_DetectOverrides`
(On/Off setting), `%AutoBrightRunning` (1 while pipeline mid-write), `%AAB_Initializing`.

- **Detect** — prof755 "Allow Override" fires on a `screen_brightness` setting change, gated by
  `Service=On ∧ AutoBrightRunning=0 ∧ Manual_Override!~true ∧ Initializing!~true ∧ DetectOverrides!~Off`
  (i.e. an external write while we weren't writing) → **task567 Manual Override**.
- **task567 Manual Override** (33 actions): waits `%AAB_CycleTime` then re-checks the guard (Stop if
  `Service=Off ∨ AutoBrightRunning=1 ∨ Manual_Override=true ∨ Initializing=true`); if `DetectOverrides=On`
  Perform **task561 Process Overrides** (record the manual point); set `Manual_Override=true`,
  `ResumeTapped=Off`; Flash + post **"Paused"** notification (tap-to-resume); QS toggle off; disable the
  **Allow Override** profile and enable **Repost Paused Notification** (prof756, code159 act22/23); if
  dimming was active, `settings put secure reduce_bright_colors_activated 0`.
- **task561 Process Overrides** records `(rounded_lux, ideal_base_brightness)` into the `%AAB_Overrides`
  array (capped at 50 entries; oldest deleted via the `top_of_loop` loop, acts 11–15). When dynamic
  compression is active it stores the de-compressed base (`%BRIGHT / ScaleDynamicCompress`, clamped ≤255).
- **Resume** — user taps the paused notification → **task569 Resume After Override**: `ResumeTapped=On`,
  cancel paused notif, Flash "Resuming", Perform **task618 Set Initial Brightness**, `Service=On`, QS toggle on.
  (Auto-resume conditions, if any beyond user tap, are in task567/prof756 — verify in S9.)

---

## 3. Derived continuity coefficients (task659 `_UpdateBrightnessFormulae`, D-002)
Recomputed whenever curve settings change (NOT stored settings):
- `Form2A = Form1A · sqrt(Zone1End)`
- `Form3A = (Zone2End · (MaxBright − (Form2A + Form2B·((Zone2End−Form2C)^0.33 − (Zone1End−Form2C)^0.33))) / MaxBright)`
- `Form2D = Zone1End` (continuity anchor, set in task570).
These guarantee C0 continuity across the 3 zones. Identical expressions appear in task570 init
(acts 15/19) with `MaxBright`→literal `255`. S5 ports as `BrightnessFormulae.deriveContinuityCoefficients`.

---

## 4. Lifecycle tasks

- **task618 Set Initial Brightness** (2 Java blocks, `java/task618_1`/`_2`): on wake (prof761) / resume,
  read current state and **set an initial brightness immediately** (without the full smoothing loop), then
  hand off to the monitor loop. Sets `%AAB_Initializing` around the write so prof755 won't mis-fire.
- **task585 Reset Brightness and State** (prof753, hibernate): disable **Monitor Ambient Light**,
  **Proximity**, **Allow Override** profiles (code159); clear `MainLoop, AutoBrightRunning, LuxAlpha,
  SmoothedLux, LastAAB, AAB_PrevBright, AAB_NetLocation`; `Throttle = AnimSteps·MinWait`; hide AAB Color
  Filter scene (code49); Disable Super Dimming if privileged; `DimmingStatus=0`; ContextResume (unless
  caller was Manual Override and screen still off).
- **task566 Reset Throttle** (prof754): restore `%AAB_Throttle` to `%AAB_DefaultThrottle` after drift.
- **task528 Panic Button** (prof769, pri 15): emergency — restore a sane brightness, clear runtime state,
  disengage dimming. The notification panic-reset action routes here (S9).

---

## 5. Runtime (non-settings) state variable table

| Variable | Meaning | Set by / lifecycle |
|---|---|---|
| `%AAB_MainLoop` | On/0 main-loop gate & re-entry guard | 554 set On; 544 set 0 on drop; 585 clear |
| `%AutoBrightRunning` | 1 while pipeline is writing | 661 act0 =1; 661 act46 =0; 585 clear |
| `%SmoothedLux` (`%smoothed_lux`) | EMA-smoothed lux | 544 (init=par1, then lux_results1); 585 clear |
| `%LuxAlpha` (`%lux_alpha`) | last smoothing alpha → animation pacing | 544 / 535 return; prox-damped ×0.1 |
| `%LastAAB` | TIMEMS of last accepted tick (throttle) | 544 act13; 585 clear |
| `%AAB_LastRawLux` | last raw lux (round3) | 554 java |
| `%AAB_LastSensorAccuracy` | last `%as_accuracy` | 554 java |
| `%AAB_CycleStart` / `%AAB_CycleTime` / `%AAB_CycleTotal` | animation timing | 554 (start=TIMEMS); 543 (wait); 544 clears CycleStart on dead-band |
| `%relative_change` / `%dynamic_threshold` | per-tick change vs sigmoid threshold | 544 java (transient) |
| `%AAB_ThreshAbsLow` / `%AAB_ThreshAbsHigh` | absolute lux dead-band (prof760 gate) | task546 Set Thresholds (NOT task570) |
| `%mapped_brightness` / `%calculated_brightness` | curve out / final brightness | 661 (transient) |
| `%AAB_CurrentBright` / `%AAB_PrevBright` | current shown / previous (read-back) | 661 act40/42; 585/661 clear PrevBright |
| `%AAB_Manual_Override` | true = paused by user override | 567 set true; 569 path resumes |
| `%AAB_ResumeTapped` | On = user tapped Resume | 567 =Off; 569 =On |
| `%AAB_Overrides` | array of manual (lux,brightness) points (≤50) | 561 push; task636 delete |
| `%AAB_DimmingStatus` / `%DimmingCurrent` / `%DimmingDS` / `%DimmingStrengthCurr` | super-dimming runtime state | 661 / 700 / 645–654; 585/661 reset |
| `%AAB_ScaleDynamic` / `%ScaleDynamicCompress` | circadian multiplier & compressed form | task90 (prof758); 548 consumes |
| `%AAB_Sun*` / `%AAB_calc_*` / `%AAB_Polar*` / `%AAB_*Duration` | solar times & polar state | task90 (S2/S6) |
| `%AAB_MorningStart/End`, `%AAB_EveningStart/End` | dawn/dusk ramp windows | task90; prof758 gate |
| `%AAB_Initializing` | true during initial-brightness write | task618; gates prof755 |
| `%AAB_Proximity` | near/far | prof759 / task545 "Detect Proximity"; damps LuxAlpha ×0.1 in 544 |
| `%AAB_Privilege` / `%AAB_SecondaryPrivilege` | detected tier | task378/643 (S2/S7) |
| `%AAB_ActiveContext` / `%AAB_ContextCache` / `%AAB_NextContextTime` | context-engine state | task43/623 (S2/S10) |
| `%AutoBrightRunning`, `%as_values1`, `%as_accuracy`, `%TRUN` | Tasker built-ins / sensor array | system |

(Full 125-var classification incl. settings in `defaults_audit.md`.)

---

## 6. Control-flow summary (pseudocode)

```
on lightSensorEvent(lux, accuracy):                      # prof760 (gated: accuracy-trust + abs dead-band + MainLoop!=On)
  MainLoop = On
  CycleStart = now; LastRawLux = round3(lux)             # task554
  evaluateLightChange(lux):                              # task544
    if running(MapLux|SmoothTransition|ProcessSensor): stop
    if LastAAB set and now-LastAAB < Throttle: MainLoop=0; stop      # throttle
    if SmoothedLux unset: SmoothedLux=lux; LuxAlpha=1; mapLux(); stop # first run
    relative_change, dynamic_threshold = sigmoidThreshold(lux,SmoothedLux,settings)
    if relative_change < dynamic_threshold: setThresholds(); MainLoop=0; stop  # dead-band
    (SmoothedLux, LuxAlpha, delta) = luxSmoothing(lux, SmoothedLux)            # task535 (round3, BigDecimal)
    if Proximity==near: LuxAlpha *= 0.1
    mapLuxToBrightness(SmoothedLux, LuxAlpha)            # task661
  mapLuxToBrightness(lux, alpha):
    AutoBrightRunning = 1
    mapped = zone1: Form1A*sqrt(lux) | zone2: Form2A+Form2B*(...) | zone3: MaxBright-(Form3A/lux)*MaxBright
    calc = ScalingUse ? compressedScale(mapped) : mapped*Scale + Offset       # task548
    calc = clamp(calc, MinBright, MaxBright)
    if PWMSensitive and calc < DimmingThreshold:
        dim = softwareDimming(calc)                       # task700
        plan = calcAnimation(alpha)                       # task543
        smoothDCTransition(calc, dim, plan); stop         # task698 (read-back override-detect)
    else if PWMSensitive and DimmingStatus and calc > DimmingThreshold:
        disableSuperDimming(); resetDimmingState()
    setDisplayBrightness(calc)                            # immediate, code810
    plan = calcAnimation(alpha)
    smoothTransition(calc, plan)                          # task696 (read-back override-detect)
    AutoBrightRunning = 0

on settingChanged(screen_brightness):                    # prof755 (guarded)
  manualOverride()                                        # task567: pause, notify, record point (561)
on resumeTapped:                                          # task569: setInitialBrightness(); Service=On
on displayOn:  setInitialBrightness()                    # prof761 / task618
on displayOff: resetBrightnessAndState()                 # prof753 / task585 (hibernate)
on throttleDrift: Throttle = DefaultThrottle             # prof754 / task566
on panic: restoreSane(); clearState()                    # prof769 / task528
```
