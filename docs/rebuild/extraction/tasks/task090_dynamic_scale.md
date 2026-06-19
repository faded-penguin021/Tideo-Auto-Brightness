# task90 — "Dynamic Scale V13 (Java) App Version"

- **Task id:** task90 (`<id>90</id>`, priority `<pri>6</pri>`)
- **XML line range:** L40051–L41290 (`Advanced_Auto_Brightness_V3.3.prj_9.xml`)
- **Action count:** 85 (sr `act0`..`act84`)
- **Two Java blocks (code 474):** Block #1 at L40429 (source L40430–L40693), Block #2 at L41085 (source L41086–L41207). Decoded copies: `docs/rebuild/extraction/_source/java/task90_1_*.java.txt`, `task90_2_*.java.txt`.

> NOTE ON ORDERING: Tasker stores `<Action>` elements alphabetically by `sr` name (act0, act1, act10, act11 … act2, act20 …), NOT in execution order. Execution order is the numeric index. The transcription below is in **numeric execution order** (act0 → act84). The XML line for each is noted.

---

## CRITICAL: Solar-source decision (drives S6)

**task90 COMPUTES sunrise/sunset/dawn/dusk/solar-noon ITSELF using inline NOAA solar equations from latitude, longitude and date. It does NOT consume Tasker built-ins `%SUNRISE` / `%SUNSET`.**

Evidence (Block #1, `task90_1_*.java`):
- Header comment L15: `/* --- NOAA Solar Calculation Algorithm (Robust Inline Fix) --- */`
- Inputs are `AAB_Latitude` / `AAB_Longitude` / `AAB_Date` (with fallbacks to `gl_latitude`/`gl_longitude`, `%LOC`, and `LocationManager.getLastKnownLocation`). L18–108.
- Full sunrise-equation algorithm computed inline in a 4-iteration loop (indices 0=Rise, 1=Set, 2=Dawn, 3=Dusk):
  - `zenithArr = { 90.8333, 90.8333, 96.0, 96.0 }` (90.8333° = standard rise/set incl. refraction; 96.0° = civil twilight). L140
  - Mean anomaly `M = (0.9856 * t_approx) - 3.289` (L157)
  - True longitude `L = M + (1.916*sin(M)) + (0.020*sin(2M)) + 282.634` (L160)
  - Right ascension via `Math.atan(0.91764 * Math.tan(...))` + quadrant correction (L164–170)
  - Declination `sinDec = 0.39782 * Math.sin(toRadians(L))` (L173)
  - Local hour angle `cosH = (cos(zenith) - sinDec*sin(lat)) / (cosDec*cos(lat))` (L177–179)
  - `H = acos(cosH)` branch for rise vs set (L187–192)
- The strings `%SUNRISE`, `%SUNSET`, `calc_sunrise`, `calc_dawn` do **not** appear in either Java block as input reads. (`ss_sunrise`/`ss_sunset` etc. are the **outputs** this block WRITES, then post-processed by Tasker actions into `%AAB_calc_*`.)

**S6 implication:** the Kotlin rebuild must port a NOAA/sunrise-equation solar calculator (lat/lon/date → rise/set/dawn/dusk/noon as epoch seconds), NOT call any Android/Tasker sunrise provider. The exact constants above are the golden reference. Polar handling (cosH out of [-1,1]) must be reproduced (see below).

---

## Java Block #1 (act43, code 474) — Solar calculator

Output: writes Tasker globals directly (no `arg1` capture var).

**Input resolution chain** (L18–108):
1. `AAB_Latitude` / `AAB_Longitude` / `AAB_Date`.
2. If lat/lng empty → `gl_latitude` / `gl_longitude`.
3. If still empty → parse `%LOC` (`"lat,lng"` split on comma).
4. Date: `Long.parseLong(AAB_Date)` else `System.currentTimeMillis()/1000` (and flag `clearDate=true`).
5. If still no location → iterate `LocationManager` providers, take freshest `getLastKnownLocation`.
6. If location ultimately not found → `setVariable("not_found","true")`, cleanup temp vars, `return`.

`clearDate`/`clearLoc` flags null out `AAB_Date`/`AAB_Latitude`/`AAB_Longitude` at end if they were originally absent (so failsafe-derived values don't persist).

**Calendar setup** (L126–130): `dayOfYear`, `year`, `zoneOffset = timezone offset / 3600000.0` (hours). **Timezone:** uses the device default `Calendar.getInstance().getTimeZone()` offset at the target instant — no explicit TZ var.

**Solar loop** (L143–199): as quoted above; per-index polar sentinels:
- `cosH > 1` → `results[i] = -1.0` (sun never rises)
- `cosH < -1` → `results[i] = -2.0` (sun never sets)
- else compute `localT = UT + zoneOffset`, store `(localT + 24) % 24`.

`riseHour=results[0]`, `setHour=results[1]`, `dawnHour=results[2]`, `duskHour=results[3]`.
**Solar noon** (L207–210): `noonHour = (riseHour+setHour)/2`, wrapped `((rise+set+24)/2)%24` if `setHour < riseHour`.

**Result write** (L222–262): `getEpoch(h, base) = base + (long)(h*3600)` where `base = startOfDay` (midnight epoch seconds of the target date).
- Polar branch (if `riseHour<0 || setHour<0`): `AAB_SunStatus="polar"`; `ss_sunlight_duration = "1440"` if `-2.0` present (midnight sun) else `"0"` (polar night); all of `ss_sunrise/ss_sunset/ss_civil_dawn/ss_civil_dusk/ss_solar_noon` set to noon (`getEpoch(12.0,...)`) as safe defaults.
- Normal branch: `AAB_SunStatus="ok"`; writes `ss_sunrise`, `ss_sunset`, `ss_civil_dawn`, `ss_civil_dusk`, `ss_solar_noon`; `durationMins = (setEpoch-riseEpoch)/60`, `+=1440` if negative; `ss_sunlight_duration = Math.round(durationMins)`.

**Writes:** `not_found`, `AAB_SunStatus`, `ss_sunrise`, `ss_sunset`, `ss_civil_dawn`, `ss_civil_dusk`, `ss_solar_noon`, `ss_sunlight_duration`; nulls `AAB_Date`/`AAB_Latitude`/`AAB_Longitude` on cleanup.

---

## Java Block #2 (act80, code 474) — Dynamic scale (tanh ramp)

Output: writes Tasker globals directly.

**Inputs** (`safeParse(var, default)`):
- `now = (System.currentTimeMillis()/1000) % 86400` (seconds-of-day, computed fresh, NOT from `%AAB_NowSS`).
- `AAB_MorningStart` (0), `AAB_MorningEnd` (0), `AAB_MorningDuration` (60)
- `AAB_EveningStart` (0), `AAB_EveningEnd` (0), `AAB_EveningDuration` (60)
- `AAB_Sunlightduration` (0)
- `AAB_PolarState` → `isPolar = polarStr.equals("true")`
- `AAB_ScaleSteepness` (4.0), `AAB_DimSpread` (0.0), `AAB_ScaleSpread` (0.0)

**Safety guards:** `morningDuration<1 → 60`, `eveningDuration<1 → 60`.

**Progress (0..1)** L52–106. Uses `time_v2 = now+86400`, `time_prev = now-86400` to handle windows that wrap across midnight (negative starts / next-day overlap). `inRange(t,s,e) = t>=s && t<e`.
- **Polar:** `progress = (sunlightDuration > 1380) ? 1.0 : 0.0`. *(The 1380-minute threshold = 23h of daylight ⇒ treat as full-day; otherwise full-night.)*
- **Non-polar:**
  - Morning ramp (now/+24h/−24h in [morningStart,morningEnd)): `progress = (t - morningStart) / morningDuration`
  - Evening ramp (now/+24h/−24h in [eveningStart,eveningEnd)): `progress = 1.0 - ((t - eveningStart) / eveningDuration)`
  - Else full-day check (`now`/`time_v2`/`time_prev` in `[morningEnd, eveningStart]`): `isDay ? 1.0 : 0.0`.
- Clamp to [0,1].

**Modifier (tanh / sigmoid)** L108–116:
```
x_factor = (progress - 0.5) * steepness
tanh_max = Math.tanh(steepness / 2.0)
tanh_raw = Math.tanh(x_factor)
modifier = (Math.abs(tanh_max) > 0.000001) ? tanh_raw / tanh_max : 0.0
```
(Normalized tanh: maps progress 0→−1, 0.5→0, 1→+1.)

**Final values** L118–123 (BigDecimal HALF_UP, scale 3):
```
dim_dynamic_raw   = 2.0 - (1.0 + (dimSpread   / 100.0) * modifier)
scale_dynamic_raw = 1.0 + (scaleSpread / 100.0) * modifier
AAB_DimDynamic    = round3_HALF_UP(dim_dynamic_raw)
AAB_ScaleDynamic  = round3_HALF_UP(scale_dynamic_raw)
```

**Writes:** `progress`, `modifier`, `AAB_DimDynamic`, `AAB_ScaleDynamic`.

---

## Scale* parameter semantics (as used in task90)

| Variable | Where used | Meaning |
|---|---|---|
| `%AAB_ScaleTransitionFactor` | act76 (389) maths | Fraction of day/night length used to widen the dawn/dusk transition windows: `delta_day = daylength * factor`, `delta_night = nightlength * factor`. Shifts MorningStart/End & EveningStart/End. |
| `%AAB_ScaleSteepness` | Java #2 | tanh steepness `k`; `tanh(k/2)` normalization. Default 4.0. |
| `%AAB_ScaleSpread` | Java #2 | Amplitude (%) of the scale swing: `AAB_ScaleDynamic = 1 + (spread/100)*modifier`. Default 0. |
| `%AAB_DimSpread` | Java #2 | Amplitude (%) of dim swing: `AAB_DimDynamic = 2 - (1 + (dimSpread/100)*modifier)`. Default 0. |
| `%AAB_ScaleTaperMidpoint` | **NOT referenced in task90** | Consumed elsewhere (not part of this task's pipeline). |
| `%AAB_ScaleTaperSteepness` | **NOT referenced in task90** | Consumed elsewhere. |
| `%AAB_ScalingUse` | **NOT referenced in task90** | Consumed elsewhere (gate is at caller / profile level). |

(Confirmed by grep over L40051–41290: only `ScaleTransitionFactor`, `ScaleSteepness`, `ScaleSpread` appear. `ScaleTaperMidpoint`, `ScaleTaperSteepness`, `ScalingUse` do not.)

---

## Action-by-action transcription (execution order)

Codes legend: 547=Variable Set, 37=If, 38=End If, 43=Else, 130=Perform Task, 135=Stop/Goto-style(Goto here), 137=Stop, 235=Custom Setting (write), 300=Anchor/Label, 319=Request Permission, 339=HTTP Request, 365=Test, 366=Get Location v2, 389=Variable Set (multi, `=`-delimited maths), 474=Java Code, 523=Notify, 548=Flash, 549=Variable Clear, 905=Set Location Mode (Custom Setting). Codes 300/319/339/365/366/523/905/235 verified in-place via `<code>` tags.

| # (sr) | XML L | Code | Name / args | Condition / label |
|---|---|---|---|---|
| act0 | 40059 | 547 | Set `%AAB_NowSS = %TIMES % 86400` (do-maths arg3=1) | — |
| act1 | 40069 | 37 | If | `%AAB_SunLastDate ~ %DATE` (op3 = matches/eq) |
| act2 | 40154 | 37 | If (label: "Goto is meant to be efficient…") | `%AAB_Latitude !set` AND `%AAB_Longitude !set` (op12 set?) — see note* |
| act3 | 40292(135) | 135 | **Goto** arg2=`Calc_sun` (arg0=1 type, arg1=1) | inside act2 — jump to anchor when lat/lon already present |
| act4 | 40352 | 38 | End If (closes act2) | — |
| act5 | 40764 | 365 | Test → `%missing_permissions()` = `CheckMissingPermissions(android.permission.WRITE_SECURE_SETTINGS)` | — |
| act6 | 40872 | 37 | If | `%missing_permissions() ~ *WRITE_SECURE_SETTINGS*` (op2 matches) |
| act7 | 40990(547) | 547 | Set `%no_write_secure = true` | inside act6 |
| act8 | 41063(38) | 38 | End If (closes act6) | — |
| act9 | 41274 | 37 | If | `%AAB_Latitude !set` OR `%AAB_Longitude !set` (op13) |
| act10 | 40079 | 319 | Request Permission `android.permission.ACCESS_BACKGROUND_LOCATION` (rationale string) | inside act9 |
| act11 | 40084 | 38 | End If (closes act9) | — |
| act12 | 40087 | 37 | If | `%AAB_Latitude !set` OR `%AAB_Longitude !set` (op13) |
| act13 | 40103 | 37 | If | `%no_write_secure ~ true` (op3) |
| act14 | 40113 | 235 | Custom Setting (write): arg0=1, name=`location_mode`, read-into `%orig_loc_mode` (capture current value) | inside act13 |
| act15 | 40121 | 43 | Else (of act13) | — |
| act16 | 40124 | 547 | Set `%orig_loc_mode = -1` (label: "Prevents this from being unset; signals no write secure settings.") | inside act13/else |
| act17 | 40135 | 38 | End If (closes act13) | — |
| act18 | 40138 | 37 | If | `%orig_loc_mode != 3` (op9) AND `%no_write_secure ~ true` |
| act19 | 40154 | 905 | Set Location Mode → 3 (high accuracy) | inside act18 |
| act20 | 40176 | 37 | If | `%caller2 ~ *scene*` (op2 matches) |
| act21 | 40186 | 30 | Wait 1 (arg1=1 unit) | inside act20 |
| act22 | 40194 | 43 | Else | `%caller2 !~ *scene*` (op3) |
| act23 | 40204 | 30 | Wait 3 | inside act22 |
| act24 | 40212 | 38 | End If (closes act20/22) | — |
| act25 | 40215 | 38 | End If (closes act18) | — |
| act26 | 40218 | 366 | Get Location v2 (timeout arg2=3500ms; populates `%gl_*`) | — |
| act27 | 40275 | 37 | If (label: "BUG: was OR not AND, needlessly called ip-api. Fixed 3.3.0.4") | `%gl_latitude !set` AND `%AAB_Latitude !set` (op13) |
| act28 | 40292 | 339 | HTTP Request GET `http://ip-api.com/json` (timeout 30; → `%http_data`) — label: "fallback for exported kid app version" | inside act27 |
| act29 | 40326 | 547 | Set `%gl_latitude = %http_data[lat]` (arg6=1) | inside act27 |
| act30 | 40342 | 547 | Set `%gl_longitude = %http_data[lon]` (arg6=1) | inside act27 |
| act31 | 40352 | 38 | End If (closes act27) | — |
| act32 | 40355 | 37 | If | `%orig_loc_mode != -1` (op7) AND `%no_write_secure ~ true` |
| act33 | 40371 | 37 | If | `%orig_loc_mode = 0` (op2) |
| act34 | 40381 | 905 | Set Location Mode → 0 (restore: off) | inside act33 |
| act35 | 40385 | 43 | Else | `%orig_loc_mode = 1` |
| act36 | 40395 | 905 | Set Location Mode → 1 | inside act35 |
| act37 | 40399 | 43 | Else | `%orig_loc_mode = 2` |
| act38 | 40409 | 905 | Set Location Mode → 2 | inside act37 |
| act39 | 40413 | 38 | End If (closes act33/35/37) | — |
| act40 | 40416 | 38 | End If (closes act32) | — |
| act41 | 40419 | 38 | End If (closes act12) | — |
| act42 | 40425 | 300 | **Anchor / Label `Calc_sun`** (Goto target of act3) | — |
| act43 | 40429 | 474 | **Java Code #1 — NOAA solar calc** (source L40430–40693) | — |
| act44 | 40698 | 37 | If | `%not_found ~ true` (op2) |
| act45 | 40708 | 37 | If | `%AAB_NotifyUse ~ true` |
| act46 | 40718 | 523 | Notify "Advanced Auto Brightness" / "Failed to retrieve Sun data today. Will use yesterday's data…" (channel `aab_privilege_notification`, icon `mw_device_brightness_auto`) | inside act45 |
| act47 | 40739 | 43 | Else (of act45) | — |
| act48 | 40742 | 548 | Flash "Failed to retrieve Sun data today…" (long, color #FF007C63) | inside act45/else |
| act49 | 40761 | 38 | End If (closes act45) | — |
| act50 | 40776 | 547 | Set `%AAB_SunLastDate = %DATE` | inside act44 |
| act51 | 40786 | 137 | **Stop** (arg0=0) | inside act44 — abort when location not found |
| act52 | 40791 | 38 | End If (closes act44) | — |
| act53 | 40794 | 37 | If | `%ss_sunlight_duration > 1380` (op7) OR `%ss_sunlight_duration < 60` (op6) |
| act54 | 40810 | 547 | Set `%AAB_PolarState = true` | inside act53 |
| act55 | 40820 | 547 | Set `%AAB_Sunlightduration = %ss_sunlight_duration` | inside act53 |
| act56 | 40830 | 43 | Else (of act53) | — |
| act57 | 40833 | 549 | Variable Clear `%AAB_Sunlightduration` | inside act53/else |
| act58 | 40840 | 547 | Set `%AAB_PolarState = false` | inside act53/else |
| act59 | 40850 | 389 | Variable Set (multi `=`): `%AAB_Sunnoon=%ss_solar_noon % 86400` / `%AAB_Sundawn=%ss_civil_dawn % 86400` / `%AAB_Sunrise=%ss_sunrise % 86400` / `%AAB_Sunset=%ss_sunset % 86400` / `%AAB_Sundusk=%ss_civil_dusk % 86400` / `%AAB_Sunlightduration=%ss_sunlight_duration` (arg5=1 do-maths, arg4 delim `=`) | — |
| act60 | 40883 | 389 | Variable Set (multi): `%AAB_calc_dawn=%AAB_Sundawn` / `%AAB_calc_sunrise=%AAB_Sunrise` / `%AAB_calc_noon=%AAB_Sunnoon` / `%AAB_calc_sunset=%AAB_Sunset` / `%AAB_calc_dusk=%AAB_Sundusk` (arg5=0 no-maths) | — |
| act61 | 40904 | 37 | If | `%AAB_calc_dawn > %AAB_calc_sunrise` (op7) |
| act62 | 40914 | 547 | Set `%AAB_calc_dawn = %AAB_calc_dawn - 86400` (do-maths) | inside act61 |
| act63 | 40924 | 38 | End If (closes act61) | — |
| act64 | 40927 | 37 | If | `%AAB_calc_noon < %AAB_calc_sunrise` (op6) |
| act65 | 40937 | 547 | Set `%AAB_calc_noon = %AAB_calc_noon + 86400` (do-maths) | inside act64 |
| act66 | 40947 | 38 | End If (closes act64) | — |
| act67 | 40950 | 37 | If | `%AAB_calc_sunset < %AAB_calc_sunrise` (op6) |
| act68 | 40960 | 547 | Set `%AAB_calc_sunset = %AAB_calc_sunset + 86400` (do-maths) | inside act67 |
| act69 | 40970 | 38 | End If (closes act67) | — |
| act70 | 40983 | 37 | If | `%AAB_calc_dusk < %AAB_calc_sunset` (op6) |
| act71 | 40993 | 547 | Set `%AAB_calc_dusk = %AAB_calc_dusk + 86400` (do-maths) | inside act70 |
| act72 | 41003 | 38 | End If (closes act70) | — |
| act73 | 41006 | 37 | If | `%AAB_calc_dawn < 0` (op6) |
| act74 | 41016 | 389 | Variable Set (multi, do-maths): add 86400 to each of `%AAB_calc_dawn/sunrise/noon/sunset/dusk` | inside act73 |
| act75 | 41037 | 38 | End If (closes act73) | — |
| act76 | 41040 | 389 | Variable Set (multi, do-maths) — schedule window builder: `%aab_daylength=%AAB_calc_sunset-%AAB_calc_sunrise` / `%delta_day=%aab_daylength*%AAB_ScaleTransitionFactor` / `%aab_nightlength=86400-%aab_daylength` / `%delta_night=%aab_nightlength*%AAB_ScaleTransitionFactor` / `%AAB_MorningStart=%AAB_calc_dawn-%delta_night` / `%AAB_MorningEnd=%AAB_calc_sunrise+%delta_day` / `%AAB_EveningStart=%AAB_calc_sunset-%delta_day` / `%AAB_EveningEnd=%AAB_calc_dusk+%delta_night` / `%AAB_MorningDuration=%AAB_MorningEnd-%AAB_MorningStart` / `%AAB_EveningDuration=%AAB_EveningEnd-%AAB_EveningStart` | — |
| act77 | 41066 | 38 | End If — *(closes the act1 `%AAB_SunLastDate ~ %DATE` block: the whole solar recompute is skipped when already computed today)* | — |
| act78 | 41069 | 547 | Set `%AAB_SunLastDate = %DATE` | — |
| act79 | 41079 | 38 | End If | — (see balance note*) |
| act80 | 41085 | 474 | **Java Code #2 — Dynamic scale (tanh)** (source L41086–41207) | — |
| act81 | 41212 | 37 | If | `%caller2 !~ *Initialize (Display On)*` (op3) |
| act82 | 41222 | 130 | Perform Task "Evaluate Light Change (Java) V2" (priority `%priority`, par1 `%SmoothedLux`) | inside act81 AND condition `%SCREEN ~ On` (op2) |
| act83 | 41245 | 38 | End If (closes act81) | — |
| act84 | 41248 | 548 | Flash "Modifier: %modifier \| Scale: %AAB_ScaleDynamic \| Progress: %progress" (channel `aab_debug`) | condition `%AAB_Debug ~ 4` |

\* **Condition operator note:** Tasker `<op>` codes used here — 2=matches(`~` regex/glob), 3=equals/matches-exact(`~`), 6=`<` (maths/less), 7=`>` (maths/greater) / also used as `!=` numeric in act32, 9=`!=`(neq), 12=Set, 13=Not Set. Operators are recorded as seen in `<op>` tags; the two `op7` usages (act32 `%orig_loc_mode != -1`, act53 `>1380`) reflect Tasker's overloaded greater/neq semantics — verify against engine when porting. End-If balance (act77/act79) closes the outer `act1` "already computed today" guard plus the act12 location-acquire guard; nesting is large and a couple of 38s pair to earlier opens — preserved verbatim, not re-derived.

---

## Variables

### Read (consumed)
`%TIMES`, `%DATE`, `%AAB_SunLastDate`, `%AAB_Latitude`, `%AAB_Longitude`, `%AAB_Date`, `%gl_latitude`, `%gl_longitude`, `%LOC`, `%no_write_secure`, `%orig_loc_mode`, `%caller2`, `%missing_permissions()`, `%http_data` (`[lat]`,`[lon]`), `%not_found`, `%AAB_NotifyUse`, `%ss_sunlight_duration`, `%ss_solar_noon`, `%ss_civil_dawn`, `%ss_sunrise`, `%ss_sunset`, `%ss_civil_dusk`, `%AAB_Sundawn`, `%AAB_Sunrise`, `%AAB_Sunnoon`, `%AAB_Sunset`, `%AAB_Sundusk`, `%AAB_calc_dawn`, `%AAB_calc_sunrise`, `%AAB_calc_noon`, `%AAB_calc_sunset`, `%AAB_calc_dusk`, `%aab_daylength`, `%aab_nightlength`, `%delta_day`, `%delta_night`, `%AAB_ScaleTransitionFactor`, `%AAB_MorningStart/End/Duration`, `%AAB_EveningStart/End/Duration`, `%AAB_Sunlightduration`, `%AAB_PolarState`, `%AAB_ScaleSteepness`, `%AAB_DimSpread`, `%AAB_ScaleSpread`, `%SCREEN`, `%priority`, `%SmoothedLux`, `%AAB_Debug`, `%modifier`, `%progress`, `%AAB_ScaleDynamic`.

### Written
- Java #1: `not_found`, `AAB_SunStatus`, `ss_sunrise`, `ss_sunset`, `ss_civil_dawn`, `ss_civil_dusk`, `ss_solar_noon`, `ss_sunlight_duration`; (nulls `AAB_Date`/`AAB_Latitude`/`AAB_Longitude` on cleanup).
- Tasker actions: `%AAB_NowSS` (act0), `%orig_loc_mode` (act14/16), `%no_write_secure` (act7), location_mode setting (act19/34/36/38, 235/905), `%gl_latitude`/`%gl_longitude` (act29/30), `%missing_permissions()` (act5), `%AAB_PolarState` (act54/58), `%AAB_Sunlightduration` (act55, cleared act57), `%AAB_Sunnoon/Sundawn/Sunrise/Sunset/Sundusk` (act59), `%AAB_calc_dawn/sunrise/noon/sunset/dusk` (act60–74), `%aab_daylength/nightlength`, `%delta_day/night`, `%AAB_MorningStart/End/Duration`, `%AAB_EveningStart/End/Duration` (act76), `%AAB_SunLastDate` (act50/78).
- Java #2: `progress`, `modifier`, `AAB_DimDynamic`, `AAB_ScaleDynamic`.

---

## Pipeline summary

1. Compute `%AAB_NowSS` (seconds of day).
2. Guard: if solar data already computed today (`%AAB_SunLastDate ~ %DATE`) **and** lat/lon present → Goto anchor `Calc_sun`, skipping permission/location acquisition.
3. Otherwise acquire location: request background-location permission, optionally flip `location_mode` to high-accuracy (only if WRITE_SECURE available), Get Location v2, fall back to ip-api.com geo-IP, then restore original location_mode.
4. **Java #1**: NOAA solar calc → epoch sunrise/sunset/dawn/dusk/noon + sunlight duration + polar status.
5. If `not_found` → notify/flash + record date + Stop.
6. Determine polar state via `ss_sunlight_duration` (>1380 or <60 ⇒ polar).
7. Normalize calc_* times into a monotonic same-day ordering (the +/−86400 fixups, act61–75).
8. Build morning/evening ramp windows from day/night length and `%AAB_ScaleTransitionFactor` (act76).
9. **Java #2**: tanh ramp over time-of-day → `progress`, `modifier`, `AAB_DimDynamic`, `AAB_ScaleDynamic`.
10. Unless caller is "Initialize (Display On)" and screen on → Perform "Evaluate Light Change (Java) V2"; debug flash if `%AAB_Debug=4`.
