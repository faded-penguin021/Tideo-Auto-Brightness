# features_spec.md — S1 ground-truth for ancillary features

Ground-truth extraction of 7 ancillary feature clusters (QS tile, foreground notification, power
calibration, debug, **formula validation**, import/export + default profiles, privilege detection/learn).
Source: `Advanced_Auto_Brightness_V3.3.prj_9.xml` (sed windows ≤150 lines, entities `html.unescape`-decoded).
Java blocks cross-referenced from `extraction/_source/java/`. Provenance line numbers stamped per item.

Action-code legend used below (from INDEX.md, verified in place):
37/38/43 = If/EndIf/Else · 47 = Show Scene · 49 = Hide Scene · 51 = Element Text(set) · 53 = Element Web JS ·
65 = Element Visibility · 126 = Return · 130 = Perform Task · 137 = Stop · 159 = Profile Status(enable/disable) ·
412 = List Files · 417 = Read File · 523 = Notify · 548 = Flash/Toast · 547 = Variable Set · 592(action) = Array
Push(split) · 779 = Notify Cancel(by title) · 808 = (QS tile collapse/refresh) · 474 = Java.
Condition `<op>`: 2 `=` · 3 `!=` · 6 `<` · 7 `>` · 12 isSet · 13 isNotSet.

---

## 1. Quick-Settings tile

### task551 `_QSToggleAABService V2` (L17325-17718, code-0 driven)
Master service ON/OFF toggle. Triggered by the QS tile tap and by resume/context callers.

**Branch decision (act0):** conditions `%AAB_Service = On` **And** `caller1 !~ *Resume After Override*`
**Or** `caller1 = *_ContextResume*`; per the validated D-021 rule (plain `And` binds tighter than `Or`):
`(%AAB_Service=On AND caller1 !~ *Resume After Override*) OR caller1 ~ *_ContextResume*` → **turn OFF**;
else **turn ON**. (Resolves the D-018 leftover; sanity-check the ContextResume leg at runtime in S9.
The Off path is the `act1..act25` block; the On path is `act27..act47`.)

**OFF path (act1-act25):**
- `%AAB_Service = Off` (act1).
- `Perform Task _MainSwitchUI V2` priority `%priority+2` (act2) — sync UI switch.
- **Disable** (code 159, arg1=0) every runtime profile, in this order (act3-act18):
  Initialize (Display On), Monitor Ambient Light, Hibernate (Display Off), Allow Override, Throttle
  Reinitialization, Dynamic Scale Engine, Proximity Detection, Repost Foreground Notification,
  Context: App Changed / Battery Changed / Location Changed / Time Changed / Location Listener /
  Location Refresher / WiFi (Dis)connected. (Allow Override disabled twice: act6 + act9.)
- `IF %AAB_NotifyUse = true` (act19): cancel notification "Advanced Auto Brightness" (779, act20);
  also cancel "Advanced Auto Brightness Paused" if `%AAB_Manual_Override != true` (act21).
- `Perform Task Dimming Decider` (act23) then `Reset Brightness and State` (act24); `Stop` (act25).

**ON path (act27-act47):**
- `%AAB_Service = On` (act27); `_MainSwitchUI V2` (act28); code 808 arg0=0 (act29, QS tile state push).
- `Perform Task Set Initial Brightness (Java) V3` priority `%priority+1` (act30).
- **Enable** (code 159, arg1=1) every runtime profile (act31-act45): Initialize (Display On), Monitor
  Ambient Light, Hibernate, Allow Override, Throttle Reinitialization, Dynamic Scale Engine, Proximity
  Detection, Repost Foreground Notification, and the 7 Context: profiles.
- `Perform Task _ForegroundNotification` priority `%priority-1` (act46); `Dimming Decider` (act47).

> State variable: **`%AAB_Service`** (`On`/`Off`) is the single source of truth. Enable/disable of the
> profile set is the Tasker analogue of starting/stopping the foreground service + its observers.

### task552 `_QSExperimentUIToggle` (L17719-17860)
Settings toggle for whether the QS tile is shown (an "experiment"). Pure 3-way state machine on
**`%AAB_QSUse`** (`true`/`false`, default `false`):
- `IF %AAB_QSUse isNotSet` → set `false` (init).
- `IF %AAB_QSUse = true` → set `false`; Element Visibility `QS_on_green=0` (code 65, scene "AAB
  Experiment Settings"); toast "Quick settings tile disabled!"; Stop.
- `IF %AAB_QSUse = false` → set `true`; `QS_on_green=1`; toast "Quick settings tile enabled!"; Stop.

---

## 2. Foreground notification

### task584 `_ForegroundNotification` (L23191-23271)
Gated by **`%AAB_NotifyUse = true`** (act0); if disabled, posts nothing.
Two priority variants chosen by caller (act1: `caller1 !~ *Repost Foreground Notification*`):
- **High-priority** variant (act2, code 523): priority `arg5=5`.
- **Low-priority** variant (act4, code 523): priority `arg5=1` (keep-alive).

Both identical otherwise:
- Title (arg0): `Advanced Auto Brightness`
- Text (arg1): `Foreground notification. Please don't dismiss this notification. It's important for
  maintaining functionality.`
- Icon (arg2 Img): `mw_device_brightness_auto`; channel/tag (arg11): `aab_setup_notification`;
  arg4=1 (ongoing), arg7=3.
- **No action buttons** in this task (arg9/arg14 empty). The user-facing tap targets live elsewhere
  (the "Paused" variant cancel is in task551).

### task692 `_NotifyToggle` (L35481-35623)
Settings toggle for the notification feature. State machine on **`%AAB_NotifyUse`** (`true`/`false`):
- `IF %AAB_NotifyUse isNotSet` → set `true` (default ON).
- `IF %AAB_NotifyUse = false` → set `true`; Element Visibility `Notify_on_green=1` (scene "AAB Misc
  Settings"); toast "Notifications enabled!"; Stop.
- `IF %AAB_NotifyUse = true` → set `false`; `Notify_on_green=0`; toast
  "Notifications disabled!<br>⚠️ Service might be stopped via aggressive battery management."; Stop.

> `%AAB_NotifyUse` default = **true** (set on first run by act0 isNotSet branch). Controls whether the
> foreground notification posts at all (task584 act0) and whether task551 cancels it on shutdown.

---

## 3. Power-draw calibration — task524 `_CalibratePowerDraw` (Java, L14247-14702)

Full Java in `extraction/_source/java/task524_1_calibratepowerdraw.java.txt`. Builds an empirical
brightness→power(mA/W) curve by sweeping the screen and reading the battery current sensor.

**Entry (`tasker.doWithActivity`):** if `%AAB_HTML_Graph8` already has data (>50 chars) show an
AlertDialog "Data Found" → **View Graph** (`skip=true`, finish) or **Recalibrate**. Otherwise show
"Calibration Prep" instructions (Airplane Mode, close apps, don't touch screen, unplug) → Start/Cancel
(Cancel sets `should_stop=true`).

**Config constants:** TARGET_POINTS=16, DISTRIBUTION_EXPONENT=0.45, MIN_STEP_DIFF=5,
NUDGE_THRESHOLD_MS=3500, MAX_WAIT_MS=12000, POST_LATCH_DELAY_MS=2000, POLL_INTERVAL_MS=200,
INITIAL_SETTLE_MS=6000.

**Pre-checks (abort → `should_stop=true`):**
- `BatteryManager.getLongProperty(2)` (CURRENT_NOW) == 0 or Long.MIN_VALUE → "Hardware sensor not responding."
- `getIntProperty(6)` (STATUS) == 2 (Charging) or 5 (Full) → "Please unplug charger."

**Measurement flow (worker thread, fullscreen Dialog with screen brightness driven directly):**
1. **Generate geometric steps:** for i in 1..16, `val=(int)(255·(i/16)^0.45)`; keep only steps that
   advance ≥ MIN_STEP_DIFF; always append 255.
2. **Ramp down** screen from `%BRIGHT` (default 128) to 0 in −2 increments (10 ms each).
3. **Baseline capture (0/255):** settle 6 s, then poll up to 20× (1 s apart) for current < 150 mA;
   record x=0 sample. Current read: `getLongProperty(2)` else `(3)`; if `abs > 50000` treat as µA→mA
   (`/1000`). Voltage from `ACTION_BATTERY_CHANGED EXTRA_VOLTAGE / 1000` (volts).
4. **Latch-breaker loop** per step: set brightness, wait up to 12 s for the current reading to *change*
   from the previous step (battery sensor latches); "nudge" brightness ±1 after 3.5 s if stalled; on
   change (or timeout) wait POST_LATCH_DELAY 2 s, then sample final mA + voltage. Records
   (brightness x, mA, W=mA/1000·V).
5. **Post-process:** if sample[0] mA > sample[1] mA, zero the baseline; subtract per-point minimum
   (mA and W) so values are net-of-idle (`max(0, raw−min)`).

**Outputs (via `tasker.setVariable`):**
- `%data` = JSON array of `{brightness, current_ma, power_w}` (net values) — the consumable dataset.
- `%part_one` / `%part_two` = HTML+JS Chart.js fragments (dual-axis Power vs Current chart). Unit
  auto-selects mW if max < 0.5 W. Back button or dismiss sets `should_stop=true` and finishes.

---

## 4. Debug levels + log surface

### task635 `_SetDebugLevel` (L28955-28978)
One action: `IF %par1 isSet → %AAB_Debug = %par1`. So `%AAB_Debug` is the debug *category* selector
value — 10 named categories, NOT a verbosity level (owner-corrected S3.5, D-023; see table below) —
set from a UI selector (`<select id=debug-selector>`, options L2772-2783, JS binding L2868-2876).

### task634 `_ShowDebugScene` (L28895-28954)
Shows scene "AAB Debug Scene" (code 47, fullscreen overlay 100×100%). If `%err` isSet AND
`%AAB_Package = *com.tideo.aab*`, hide+re-show (refresh) the scene. (Self-debug guard for the dev build.)

### `%AAB_Debug` level semantics — 10 named categories (authoritative; owner-verified S3.5, D-023)

The Debug scene's selector (XML L2773–2782) names every level. They are **distinct info categories,
shown mostly as toasts — NOT verbosity tiers**:

| Level | Label (verbatim from the selector) |
|---|---|
| 0 | Off |
| 1 | Skip Animations |
| 2 | Animation Details |
| 3 | Light Eval Thresholds |
| 4 | Dynamic Scale Calcs |
| 5 | Super Dimming Info |
| 6 | Overlay Preview |
| 7 | Graph Metrics |
| 8 | Context Automation |
| 9 | Context Location |

- **Default 0** (off). Selector-driven integer; default `'3000'`-style fallbacks elsewhere are unrelated.
- Conditions across the project gate on it almost exclusively with **op 2 (`=`)** — consistent with the
  discrete categories above (one `op 3 !=1` at L16386 — "any debug on"). Histogram of equality targets:
  1×2, 2×2, 3×1, 4×1, 5×4, 6×1, 7×7, 8×1, 9×1.
- Level 8 "Context Automation" gates the context-evaluation log (task623 `isDebug=AAB_Debug.equals("8")`
  writes `%eval_log` "Veto: …" diagnostics, L12183; task637 "System restored missing Default.json").
  ⚠️ S1's *inferred* glosses were wrong here — 7 is **Graph Metrics**, not "pipeline-math logging".
- Levels gate `code 548` toasts tagged `aab_debug` and `%eval_log`/`%err` surfaces.

> Rebuild note: model `%AAB_Debug` as an enum 0–9 with the labels above (equality-matched category
> selector, NOT a monotonic severity threshold). Default 0. S12 UI = dropdown with these exact labels.

---

## 5. Formula validation (CRITICAL)

Two distinct validators. **task583** is a *visual* validator (marks scene fields red); **task707** is
a *safety* validator returning a yes/no flag. Both operate on the derived curve coefficients.

### task583 `_RedInvalidFormulae` (L23084-23190) — visual field flagging (code 51 = set Element Text)
Scene: "AAB Brightness Settings". Each rule sets a scene element's HTML to a red/`#FFC107` styled
variant when invalid, or the plain variant when valid.

| Field | Rule (invalid when) | Element / effect |
|---|---|---|
| `%aab_form2a` | `< 0` (op 6) | el `Form2a_calculated` → red `%aab_form2a (automatic)`; else plain `%aab_form2a (auto)` |
| `%aab_form3a` | `< 0` (op 6) | el `Form3a_calculated` → red `%aab_form3a (auto)`; else plain `%aab_form3a (auto)` |
| `%aab_form2c` | `> %aab_zone1end` (op 7) | el `form2c_label` ("Zone 2 Offset") → red bold; else plain |

> These are continuity/sanity guards on the *derived* coefficients (form2A, form3A are derived per
> CLAUDE.md; form2C must stay inside zone 1). No abort — purely advisory styling. Rebuild: surface as
> per-field inline error highlight on the curve-settings screen.

### task707 `_ValidateBrightnessParams` (L38095-38256) — safety check, returns `%is_safe` (yes/no)
Computes the predicted brightness **at 1000 lux** using whichever zone the curve places 1000 lux in,
then warns if it is dangerously dim. All maths via code 547 with `arg3=1` (DoMaths).

Logic:
1. `%max_b` = `%AAB_MaxBright` (default 255 if `%AAB_MaxBright` isNotSet) — act0-act4.
2. Select zone formula for lux=1000 (act5-act11):
   - `IF %aab_zone1end > 1000` → **Zone 1:** `%safe_val = %aab_form1a · sqrt(1000)` (act6).
   - `ELSE IF %aab_zone2end > 1000` → **Zone 2:**
     `%safe_val = %aab_form2a + %aab_form2b·((1000−%aab_form2c)^0.33 − (%aab_form2d−%aab_form2c)^0.33)` (act8).
   - `ELSE` → **Zone 3:** `%safe_val = %max_b − (%aab_form3a/1000)·%max_b` (act10).
3. **Safety bound (act12-act17):** `IF %safe_val < 25` → toast (code 548)
   "⚠️ Safety Warning: Brightness too low at 1000 Lux (%safe_val / 255). Please adjust parameters."
   and `%is_safe = no`; **else** `%is_safe = yes`.
4. `Return %is_safe` (act18, code 126).

| Field/derived | Rule / bound | Error / outcome |
|---|---|---|
| predicted brightness @1000 lux (`%safe_val`) | **`< 25`** (of 255) | toast "Safety Warning: Brightness too low at 1000 Lux"; `%is_safe = no` |
| `%AAB_MaxBright` | isNotSet | defaults to 255 (no error) |
| zone selection | `zone1end>1000` ? z1 : `zone2end>1000` ? z2 : z3 | picks formula, no error |

> **Validation-rule count: 5 distinct rules total** — 3 advisory (task583: form2a<0, form3a<0,
> form2c>zone1end) + 2 safety (task707: predicted brightness@1000lux<25 → unsafe; MaxBright default 255).

---

## 6. Import/export + default profiles

### Profile data format (JSON on disk)
Path: `/storage/emulated/0/Download/AAB/configs/<name>.json` (filename sanitized
`[^a-zA-Z0-9.\- ]→_`, `.json` appended). Schema (`task637.performSave`, `task592.getBaseProfile`):

```
meta:        {name, version (=%AAB_Version), timestamp}
general:     {z1_end, z2_end, form1a, form2a, form2b, form2c, form2d, form3a}
misc:        {min_bright, max_bright, scale, offset, anim_steps, min_wait, max_wait,
              delta_factor, throttle}
reactivity:  {detect_overrides, thresh_dark, thresh_dim, thresh_bright, thresh_steepness,
              thresh_midpoint, trust_unreliable}
circadian:   {spread, transition, steepness, enabled, taper_mid, taper_steep, qs_use}
superdimming:{enabled, threshold, strength, exponent, spread, pwm_exp, pwm_sensitive}
```
Maps 1:1 to `%AAB_*` globals (e.g. `general.z1_end`↔`%AAB_Zone1End`, `circadian.enabled`↔
`%AAB_ScalingUse`, `superdimming.pwm_sensitive`↔`%AAB_PWMSensitive`). Booleans stored as JSON bools;
on load mapped to `On`/`Off` (reactivity) or `true`/`false` (circadian/superdimming).

### task637 `_ProfileManager` (Java, L29304-29611) — the import/export engine
Dispatch on `%par1` (MODE), data in `%par2`:
- `SAVE_FILE` / `SAVE_FILE_SILENT` → `performSave`: reads all `%AAB_*` globals (with hard-coded
  fallbacks), writes pretty JSON. Sets `%saved`.
- `LOAD_FILE` → `performLoad`: reads JSON, writes each present key back to its `%AAB_*` global
  (`setG` also accumulates `%ui_update_json`). **Self-healing:** if loading "Default" and the file is
  missing, regenerates an emergency Default.json inline. **Baseline protection:** only updates
  `%AAB_ProfileUser` when `isManualLoad` (caller is not `_EvaluateContexts`). After load,
  **recomputes derived coeffs**: `form2A = round3(form1a·sqrt(z1_end))`;
  `form3A = round0( z2_end·(max_bright − (form2A + form2b·(|z2_end−form2c|^0.33 − |z1_end−form2c|^0.33))) / max_bright )`.
  Sets `%loaded`, `%ui_update_json`.
- `DELETE_FILE` → `performDelete`: refuses to delete "Default"; on success if the deleted profile was
  `%AAB_ProfileUser`, falls back base to "Default". Sets `%deleted`.
- Errors surface via `%err_msg`. All four result vars cleared at entry.

### task592 `_CreateDefaultProfiles` (Java L24133-24360 + wrapper L24125-24428)
Java generates **5 default profiles** to disk via `writeProfile`, all from `getBaseProfile()`:
- **Default** — baseline (z1_end 35, z2_end 10000, form1a 5, form2a 29.58, form2b 8.8, form2c 18,
  form2d 35, form3a 2513; min/max bright 10/255; scale 1, offset 0; anim_steps 50, min/max_wait 5/30,
  throttle 1510, delta_factor 1.8; thresh dark/dim/bright 0.3/0.25/0.08, steepness 2.1, midpoint 3.0;
  circadian disabled; superdimming disabled).
- **Battery Saver** — max_bright 200, min_bright 1, scale 0.8, anim_steps 1, delta_factor 2.8;
  thresh dark/dim/bright all 0.5; both features off.
- **Video Streaming** — anim_steps 50, min/max_wait 50/100, min/max_bright 20/255, delta_factor 0.5,
  throttle 5010; thresh_bright 0.3, thresh_dark 0.4; form1a 6, form2b 8.8; circadian off;
  **superdimming ON** (threshold 20).
- **Outdoors** — min_bright 25, offset 15, scale 1.15, anim_steps 10, min_wait 10, delta_factor 4;
  form1a 8, z1_end 55, z2_end 18000; **recomputes form2a = round3(form1a·sqrt(z1_end))** (HALF_UP);
  superdimming off.
- **Night Reading** — superdimming pwm_sensitive ON (enabled false), threshold 15; min_bright 1,
  min/max_wait 60/120, delta_factor 0.8, throttle 6010; thresh_dark 0.6; circadian off.

**Wrapper (task592 actions):** after the Java (act0), if `%par1 = FROM_PROFILE_SCENE` (act1): toast
"Restoring files…", wait, List Files `Download/AAB/configs *.json` → `%current_files` (412), push-split
(592 action), and refresh the profile-scene web list via JS (code 53). So this task both *seeds* the
default set and *repopulates* the picker.

### task622 `_ShowProfileScene` (L26711-26832) — the profile picker UI
- Reads `Download/AAB/configs/contexts.json` → `%aab_contexts_json` (417, append=1).
- Lists `Download/AAB/configs/*.json` → `%files` sorted (412, arg4=7).
- `IF %files(#) > 0` → array-push split `%files` on `,` (592 action), build `%aab_file_list_json` =
  `["%files(+",")"]`; else `[]`.
- Shows scene "AAB Profile" (47). Dev-build refresh guard mirrors task634.

> Import/export mechanism = **file-based JSON in Download/AAB/configs/**, driven entirely by task637
> (save/load/delete) with task592 seeding defaults and task622 browsing them. There is no proprietary
> binary format — straight `org.json` pretty-printed JSON. Rebuild target: DataStore-backed profiles +
> JSON import/export keeping this exact schema for interop with existing user files.

---

## 7. Privilege detection / learn

### Privilege tiers — task378 `_PrivilegeDetection V5` (Java L9469-9622)
Sets **`%AAB_Privilege`** by probing, in priority order, caching the result (skips if already set and
not "None"). Caller `*Set Initial Brightness*` forces a re-detect (clears cache first).

Detection order (first hit wins → `hasPrivilegeFound`):
1. **Root** — `su -c id`; if exit 0 and stdout contains `uid=0` → `%AAB_Privilege = "Root"`.
   (Uses stderr drainer + 2 s killer thread instead of `waitFor(timeout)`.)
2. **Write Secure** — `PackageManager.checkPermission("android.permission.WRITE_SECURE_SETTINGS",
   pkg) == GRANTED` → `"Write Secure"`.
3. **Shizuku** — `tasker.getShizukuService("package") != null` → `"Shizuku"`.
4. **ADB WiFi** — read port from Tasker pref `adbwp` else `getprop service.adb.tcp.port` (default 5555);
   TCP connect `127.0.0.1:port` (200 ms) succeeds → `"ADB WiFi"`.
5. **Fallback** → `"None"` + toast "⚠️ Unprivileged will draw a semi-transparent overlay and eat your
   battery. Not recommended!"

> **Sanctioned deviation (owner, S3.5 → D-024):** step 4 reads the **Tasker pref `adbwp`** for the ADB
> port — a Tasker-host artifact with no business in a standalone app. The rebuild must NOT read Tasker
> prefs (and drops ADB-WiFi probing altogether): elevated-tier truth is
> `checkPermission(WRITE_SECURE_SETTINGS)`; ADB / Shizuku / root remain *grant channels* only (matches
> the S7 PrivilegeManager + S11 onboarding design and D-016).

If caller `~ *anon*` and final privilege ≠ None, toasts "Current privilege: <x>."

> Tier mapping to CLAUDE.md model: `Root` / `Write Secure` / `Shizuku` / `ADB WiFi` are all the
> **ELEVATED** tier (WRITE_SECURE_SETTINGS effectively available → super dimming). `None` = unprivileged
> overlay path. The user-grantable **BASIC** (WRITE_SETTINGS) tier is checked separately in task563
> (`Settings.System.canWrite`), not in task378.

### task643 `_LearnWriteSecure` (Java L30506-30637) — the ADB grant teach flow
Param `%AAB_Package` (required; throws if null). If the package already holds
WRITE_SECURE_SETTINGS → returns immediately. Otherwise builds:
`adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` and shows a blocking AlertDialog
"Permission Required" explaining it must be run from a computer, with buttons **Done** / **Cancel** /
**Copy Command** (neutral button copies to clipboard without dismissing). Pure teaching UI — it does
NOT grant; it instructs the user to run ADB. (Shizuku is the grant channel elsewhere, not here.)

### task563 `_AskPermissionsV7` (Java L19678-20042) — the BASIC/runtime permission wizard

> **Sanctioned deviation (owner, S3.5 → D-024):** the 8 capability gates below and their order are the
> parity contract; the dialog-polling *flow* is not. S11 replaces it with modern Compose onboarding
> (ActivityResultContracts + lifecycle re-checks), as its RUNBOOK brief already specifies.

Sets `%AAB_Package = context.getPackageName()`. Polling loop that walks the user through 8 sequential
permission "steps", each with a Grant dialog + the matching Settings intent, re-checking each pass:
1. **Write Settings** (`Settings.System.canWrite`) — the BASIC tier gate.
2. **Display Overlay** (AppOps `system_alert_window`) — with a "restricted settings" troubleshooting
   sub-flow (greyed-out switch → ACTION_APPLICATION_DETAILS_SETTINGS "Allow restricted settings").
3. **Usage Stats** (AppOps `get_usage_stats`).
4. **All-Files / Storage** (`Environment.isExternalStorageManager()` on SDK≥30; legacy
   WRITE_EXTERNAL_STORAGE popup below 30).
5. **Location (Always)** — FINE + BACKGROUND on SDK≥29.
6. **Notifications** (`nm.areNotificationsEnabled()`; POST_NOTIFICATIONS runtime popup on SDK≥33).
7. **Battery Optimization** (`isIgnoringBatteryOptimizations`).
8. **Exact Alarms** (SDK≥31 `canScheduleExactAlarms`).

When all 8 pass → **`%AAB_PermGranted = "3"`** and returns "Success". Cancel → returns "Cancelled".

> **`%AAB_PermGranted`** is the gate flag (`"3"` = fully granted). **`%AAB_Privilege`** is the elevated
> tier (Root/Write Secure/Shizuku/ADB WiFi/None). `%AAB_Package` is the resolved package, shared by all
> three tasks. BASIC = Write Settings (task563 step 1); ELEVATED = anything task378 detects ≠ None.

---

## Summary (6 lines)

1. **Validation rules: 5 total.** task583 (3 advisory, code-51 red field flags): form2A<0, form3A<0,
   form2C>zone1End. task707 (2 safety): predicted brightness@1000lux<25/255 → `%is_safe=no` + warning
   toast; `%AAB_MaxBright` defaults 255 when unset.
2. task707 picks the zone formula for lux=1000 (z1 sqrt if zone1End>1000, else z2 cube-root-blend if
   zone2End>1000, else z3 reciprocal) — validation must mirror the live curve math, not a fixed formula.
3. **Privilege detection (task378)** sets `%AAB_Privilege` by first-hit probe in order Root (`su -c id`
   →uid=0) → Write Secure (checkPermission) → Shizuku (`getShizukuService`) → ADB WiFi (TCP 127.0.0.1:port)
   → None; result cached unless caller forces re-detect.
4. Root/Write Secure/Shizuku/ADB WiFi all = **ELEVATED** (super dimming); None = unprivileged overlay.
   **BASIC** (WRITE_SETTINGS) is a separate gate in task563 step 1; `%AAB_PermGranted="3"` means all 8
   runtime permissions granted.
5. task643 only *teaches* the `adb shell pm grant … WRITE_SECURE_SETTINGS` command (copy-to-clipboard
   dialog); it never grants. Grant channels are ADB/Shizuku/root, exercised outside this task.
6. QS tile (`%AAB_Service`), notification (`%AAB_NotifyUse`, default true), QS-show (`%AAB_QSUse`,
   default false) and debug (`%AAB_Debug`, enum 0–9, equality-matched) are all simple flag state
   machines; profiles are file-based `org.json` JSON under Download/AAB/configs/ (5 seeded defaults).
