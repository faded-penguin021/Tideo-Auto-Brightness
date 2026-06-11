# profiles.md — pipeline-triggering profiles (S1)

Covers the **10 pipeline profiles owned by S1**: prof753, 754, 755, 756, 757, 758, 759, 760, 761, 769.
Context profiles 762–768 + prof8 belong to **S2** (skipped here).

Extraction notes:
- Profiles carry `ve="2"`; matched with awk pattern `/<Profile sr="profNNN"/` (no closing `>`) per XML_RECIPES R4.
- `<mid0>` = enter task, `<mid1>` = exit task, `<pri>` = profile priority.
- A profile **fires its enter task only when its context is active AND its `<ConditionList sr="if">` gate passes**. These gates are load-bearing pipeline logic (D-003).
- Condition op codes (empirically derived, project-wide histogram): `2 ==` · `3 !=` · `6 <` · `7 >` · `9 >=` · `0 ~(matches)` · `1 !~` · `12 isSet` · `13 isNotSet`.
- **ConditionList boolean semantics (owner-verified 2026-06-11, D-021):** plain `And`/`Or` bind *tighter* and form inner sub-expressions (with conventional `And` > `Or` precedence); `And2`/`Or2` are the *outer* joins between those sub-expressions, evaluated left-to-right. Validated against prof760 (owner confirmed the staged semantics) and prof758 (rule yields the only design-sensible reading). Resolves INDEX.md unresolved #1 / D-009.
- ⚠️ **ConditionList children are stored ALPHABETICALLY in the XML** (`bool10` sorts before `bool2`, `c10` before `c2`) — re-sort numerically before transcribing. S1's prof758 bool sequence was scrambled by this (fixed below; D-021, recipe R4 updated).

| Profile | Line | Enter | Exit | Pri | Context (code) |
|---|---|---|---|---|---|
| prof753 Hibernate (Display Off) | L3 | task585 | — | 3 | Event **Display Off** (210) |
| prof754 Throttle Reinitialization | L16 | task566 | — | 0 | State (123, arg0=1) |
| prof755 Allow Override | L56 | task567 | — | 10 | Event **Setting changed** `screen_brightness` (2075, arg0=2) — the ContentObserver equivalent |
| prof756 Repost Paused Notification | L111 | task567 | — | (dflt) | State (165) |
| prof757 Repost Foreground Notification | L156 | task584 | — | (dflt) | Time/periodic (165) |
| prof758 Dynamic Scale Engine | L195 | task90 | — | (dflt) | Time/periodic (165) |
| prof759 Proximity Detection | L300 | task545 | task545 | 4 | State **Proximity** (125, arg0=1) |
| prof760 Monitor Ambient Light | L318 | task554 | — | 7 | Event **Light Sensor** (2088, type=5, 1000 ms) |
| prof761 Initialize (Display On) | L386 | task618 | — | 7 | Event **Display On** (208) |
| prof769 Panic (Reset) | L722 | task528 | — | 15 | Event 2083 (shake/sig-motion) + State 120/3 (upside down) + State 123/1 |

---

## prof760 "Monitor Ambient Light" — THE sensor gate (D-003)

- **Context:** `Event` code **2088** = Sensor event. `arg1=5` (sensor type 5 = **light**), `arg2=1000` (1000 ms min report interval), `arg3=1`, `arg4=0`. Declared relevant vars: `%as_accuracy` (1=Low,2=Med,3=High), `%as_sensor_type`, `%as_values()` (array; `%as_values1` = lux).
- **Enter task:** task554 (Process Sensor Event). **Priority 7.**
- **Gate `<ConditionList>`** (verbatim, conditions c0..c5 with bool sequence `[Or2, And, And2, Or, And2]`):

  | c | clause | join-after |
  |---|---|---|
  | c0 | `%AAB_TrustUnreliable = On` | Or2 |
  | c1 | `%AAB_TrustUnreliable = Off` | And |
  | c2 | `%as_accuracy > 1` | And2 |
  | c3 | `%as_values1 < %AAB_ThreshAbsLow` | Or |
  | c4 | `%as_values1 > %AAB_ThreshAbsHigh` | And2 |
  | c5 | `%AAB_MainLoop != On` | — |

- **Confirmed reading** (owner-verified 2026-06-11, D-021 — plain And/Or = inner groups, And2/Or2 = outer joins left-to-right):
  `((TrustUnreliable=On) OR (TrustUnreliable=Off AND as_accuracy>1)) AND ((as_values1 < ThreshAbsLow) OR (as_values1 > ThreshAbsHigh)) AND (MainLoop != On)`
  Three staged gates, evaluated in order:
  1. **Accuracy-trust gate:** either the user trusts unreliable readings, or the reading must be accuracy>1 (Medium/High). Low-accuracy readings are dropped unless `TrustUnreliable=On`.
  2. **Absolute-threshold dead-band:** the profile only re-fires when lux has moved **outside** the absolute band `[ThreshAbsLow, ThreshAbsHigh]` — this is a hysteresis/anti-jitter gate. `ThreshAbsLow/High` are set by **task546 _Set Thresholds_**, not task570.
  3. **Main-loop mutex:** `MainLoop != On` — owner-confirmed polarity: re-entrancy/mutex guard, skip while the main loop is already running.

## prof753 "Hibernate (Display Off)" → task585
- **Context:** Event **Display Off** (code 210). No gate. **Pri 3.**
- Fires task585 (_Reset Brightness and State_) on screen-off: unregister/idle, reset runtime state (smoothedLux, cycle vars, dimming) per hibernate semantics.

## prof761 "Initialize (Display On)" → task618
- **Context:** Event **Display On** (code 208). No gate. **Pri 7.**
- Fires task618 (Set Initial Brightness) on wake — computes & sets the initial brightness before the monitor loop resumes (D: pairs with throttle reset prof754).

## prof754 "Throttle Reinitialization" → task566
- **Context:** State (code 123, arg0=1).
- **Gate:** `%AAB_Throttle != %AAB_DefaultThrottle` **And** `%LastAAB isSet`.
- Fires task566 (_Reset Throttle_) to restore the throttle window to default after a transient change. Pri 0.

## prof755 "Allow Override" → task567
- **Context:** Event **Setting changed** (code 2075) on `screen_brightness`, `arg0=2`. This is the **ContentObserver-on-Settings.System.SCREEN_BRIGHTNESS** equivalent — fires when the brightness setting changes externally.
- **Gate:** `%AAB_Service = On` **And** `%AutoBrightRunning = 0` **And** `%AAB_Manual_Override !~ true` **And** `%AAB_Initializing !~ true` **And** `%AAB_DetectOverrides !~ Off`.
  - i.e. only treat an external brightness write as a *manual override* when: the service is on, our own pipeline is not mid-write (`AutoBrightRunning=0`), we are not already in override, not initializing, and override detection is enabled.
- Fires task567 (Manual Override) → pause. **Pri 10** (high, beats the monitor profile). This is the suppress-echo / override-detect mechanism (maps to S7 BrightnessObserver + S9 OverrideMonitor).

## prof756 "Repost Paused Notification" → task567
- **Context:** State (code 165).
- **Gate:** `%AAB_Service !~ On` **And** `%AAB_ResumeTapped !~ On` **And** `%SCREEN = On` **And** `%AAB_Manual_Override = true`.
- Keeps the "paused (manual override active)" notification posted while paused and screen on; routes back through task567.

## prof757 "Repost Foreground Notification" → task584
- **Context:** Time/periodic (code 165).
- **Gate:** `%SCREEN = On` **And** `%AAB_Service = On` **And** `%AAB_Manual_Override !~ true`.
- Periodically reposts the foreground/running notification (task584) while active and not overridden.

## prof758 "Dynamic Scale Engine" → task90
- **Context:** Time (con0, repeat every 2 min) + State (con1, code 165) carrying the gate.
- **Gate** (c0..c13; bool sequence corrected in S3.5 — S1's `[…And2 in 5th position…]` was an artifact of the XML's ALPHABETICAL child ordering (D-021). Numerically re-sorted it is twelve plain joins, then one final `And2`: `[And, Or, And, Or, And, Or, And, Or, And, Or, And, Or, And2]`):
  - c0∧c1: `MorningStart < %TIMES%86400 < MorningEnd` · c2∧c3: same window at `+86400` · c4∧c5: same at `−86400` (day-wrap variants)
  - c6∧c7: `EveningStart < %TIMES%86400 < EveningEnd` · c8∧c9: at `+86400` · c10∧c11: at `−86400`
  - c12: `%AAB_SunLastDate != %DATE` (sun data stale for today) · c13: `%AAB_ScalingUse = true`
- **Reading (validated D-021 rule):**
  `((c0∧c1) ∨ (c2∧c3) ∨ (c4∧c5) ∨ (c6∧c7) ∨ (c8∧c9) ∨ (c10∧c11) ∨ c12) AND (ScalingUse = true)`
  — fires task90 (Dynamic Scale V13) when circadian scaling is enabled AND (now is inside a dawn/dusk ramp window, with ±1-day wrap variants, OR sun times need recomputing today). Window math owned by S2 (task090 doc) + S6.

## prof759 "Proximity Detection" → task545 (enter & exit)
- **Context:** State **Proximity** (code 125, arg0=1). **Pri 4.**
- Enter and exit both run **task545 "Detect Proximity"** (XML L16424; transcribed S3.5 → `tasks/task545_detect-proximity.md`): `If %caller1 ~ *enter* → %AAB_Proximity = near; Else-If %caller1 ~ *exit* → %AAB_Proximity = far`.
- **It does NOT pause the pipeline** (owner-verified, D-022): `near` only *damps* smoothing — task544 act28–29 sets `LuxAlpha = lux_results2 × 0.1`, so brightness reacts an order of magnitude slower while the phone is at the ear / in a pocket.

## prof769 "Panic (Reset)" → task528
- **Contexts (all must hold; XML L722–743, verified S3.5/D-022):** Event **2083** (significant-motion/shake trigger, no args) **+** State **120 arg0=3** (Orientation: *upside down*) **+** State **123 arg0=1** (same state family as prof754; label inferred — display-active). **Pri 15** (highest — beats everything).
- Owner-verified semantics: emergency escape hatch when the screen is black due to misconfiguration — **flip the phone upside down and shake**. task528 (Panic Button) sets brightness to max, disables the event listeners/profiles, and sets `%AAB_Service = Off` (full stop). (Gate-1 / S9 notification panic-reset action.)
