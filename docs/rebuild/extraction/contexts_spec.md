# contexts_spec.md — Context-Override System (S2 ground-truth)

The **context-override system** lets the user define rules ("contexts") that swap the *entire
active brightness profile* based on the current **app / Wi-Fi SSID / battery / time-of-day /
location**. It is layered on top of the core pipeline: 8 Tasker profiles (prof762–768 + prof8)
watch the environment, gate themselves cheaply, and hand off to one Java evaluator (`task43
_EvaluateContexts V2`) which picks a winning rule from `contexts.json` and applies its profile.

Sources verified against XML this session: prof762–768, prof8 (awk-extracted), task26, task624
head, task43 pre/post-Java actions. Java logic from already-decoded
`docs/rebuild/extraction/java/{task43,623,625,626,630,631,633,105}`.

Op-code legend (from `profiles.md`, project histogram): `2 ==` · `3 !=` · `12 isSet` ·
`13 isNotSet` · `0 ~matches` · `6 <` · `7 >` · `9 >=`.

---

## 1. Profile registry (the 8 watchers)

All context profiles share `<flags>8`, `clp=true`, and **`<pri>0`** (or no `<pri>` at all) at
the profile/event level. **Profile `<pri>` is NOT the precedence mechanism** — every context
profile has priority 0. Real precedence lives in the per-rule `priority` integer inside
`contexts.json`, resolved in task43 (see §4). Correcting the brief: there is no profile-`<pri>`
precedence matrix; the matrix is over rule priorities.

| Profile | Trigger (context) | code(s) | Enter task (mid0) | Exit (mid1) | Gate cache token |
|---|---|---|---|---|---|
| prof762 App Changed | Event: Application changed | 2078 | 43 `_EvaluateContexts V2` | — | `*.*` (any) |
| prof763 Battery Changed | Event: Battery Changed | 203 | 43 | — | `*[BATT]*` |
| prof764 Time | State: Time `%AAB_NextContextTime`→`%AAB_NextContextTime` | Time(con0) + State 165 | 43 | — | (none; time-var gate) |
| prof765 Location Listener | State 165 only | 165 | 630 `_ContextLocnListener V4` | **630** | `*[LOC]*` |
| prof766 Location Refresher | Time repeat every **3 min** (`rep=2 repval=3 fh/fm/th/tm=-1`) + State 165 | Time + 165 + State 123 | 631 `_ContextF5NetLoc V8` | — | `*[LOC]*` |
| prof767 Location Changed | Event: Variable Set `%AAB_NetLocation` + State 165 + State 123 | 3050 | 43 | — | `*[LOC]*` |
| prof768 WiFi (Dis)connected | State: Wi-Fi Connected (any SSID) + State 165 | 160 + 165 | 43 | **43** | `*[WIFI]*` |
| prof8 Reset Serialized Cache | Time **03:00–03:00** daily + State 165 | Time + 165 | 26 `_ResetContextCacheDaily` | — | (fires while cache IS set) |

Notes on triggers verified in XML:
- **prof762** State con2 is code 123 `Int arg0=1` (variable-value state, the standard "service
  alive" latch used project-wide). Event 2078 declares relevant vars `%app_icon/%app_name/%app_package`.
- **prof764** uses a Tasker **Time context** `<fromvar>/<tovar> = %AAB_NextContextTime`: the
  profile becomes active at the clock time stored in that variable (format `HH.MM`, set by
  task43 §4). This is the self-scheduling wake-up for time-range rules and SUNRISE/SUNSET.
- **prof766** `<rep>2 <repval>3` with all `fh/fm/th/tm=-1` = **repeat every 3 minutes, all day**.
  This is the location heartbeat/watchdog poll. It also has a State 123 latch (con2).
- **prof767** Event 3050 = Variable Set, watching `%AAB_NetLocation` (`arg2=0`). Fires whenever
  the listener/refresher writes a new location.
- **prof768** State 160 = Wi-Fi Connected, all SSID args blank (`arg3=2 arg4=1` ⇒ match any
  connect/disconnect). mid0 **and** mid1 = 43, so both enter and exit run the evaluator.
- **prof765** has **no event/time** — it is a pure State profile (165). mid0=mid1=630, so the
  location listener is (re)registered on enter and torn down on exit.

### 1.1 The shared gate (`<State code=165>` ConditionList) — verbatim semantics

Every watcher except prof8 carries a 4-clause `code=165` expression state, all joined `And`.
With op `3 = !=` and op `2 = ==`, the gate fires **only when**:

```
%AAB_Manual_Override  != true        (c: manual override is OFF)
%AAB_Service          == On          (service running)
%AAB_ContextOverride  != true        (a context override is NOT already latched)
%AAB_ContextCache     matches <token> (op 2 == with wildcards: *[BATT]* / *[LOC]* / *[WIFI]* / *.*)
```

prof764 replaces the cache-token clause with `%AAB_NextContextTime isSet (op 12)` (a scheduled
time exists). Clause **order differs per profile** (e.g. prof763 puts the cache clause before
the override clause) but the join is uniformly `And`, so order is immaterial.

Meaning of the two override flags (both must be *false* for a watcher to act):
- **`%AAB_Manual_Override`** — user hit the manual brightness slider / panic; suspends all
  automatic logic. If `true`, no context watcher runs.
- **`%AAB_ContextOverride`** — a *manual context lock* (user pinned a specific profile). If
  `true`, watchers still don't fire the *switch*; task43, if reached by another path, also skips
  the profile switch but still updates wake times (§4, PASS 4 `else` branch).

The **`%AAB_ContextCache` token gate** is the cheap pre-filter: a watcher only bothers calling
the heavy evaluator if at least one configured rule uses that signal type. Built by
`_ContextManager` (§3).

prof8's gate is a single clause `%AAB_ContextCache isSet (op 12)` — only reset the RAM cache if
there is one.

---

## 2. Serialized cache formats

Two distinct caches, both rebuilt by `_ContextManager` (task623) on every save/delete:

### 2.1 `%AAB_ContextCache` — the cheap token + app-list string
Format (built in task623 lines 93–103):
```
[BATT][LOC][WIFI],pkg1,pkg2,pkg3,
```
- Leading **flag tokens** present only if ≥1 rule uses that signal: `[BATT]`, `[LOC]`, `[WIFI]`
  (always in that order). Used by profile gates (`*[BATT]*` etc.) and by task43 veto logic.
- Then a comma, then the **deduplicated set of every app package** referenced by any rule
  (HashSet → unordered), each followed by a comma. So an app package is detectable via the
  substring test `cache.contains("," + curApp + ",")` (task43 L211) — note the **comma
  delimiters**, which is why the string both starts (after tokens) and ends with `,`.
- Empty config ⇒ value is just `","` (single comma) or unset. prof8 treats unset as nothing to reset.

### 2.2 `%AAB_ContextJSONCache` — the full RAM copy of contexts.json
- Set to `contextList.toString()` (compact JSON array) by task623 (L106) on every mutation.
- task43 reads it first (PASS 3) to avoid disk I/O. If empty/corrupt (`length() <= 2`), or after
  the daily reset clears it, task43 falls back to reading
  **`/storage/emulated/0/Download/AAB/configs/contexts.json`** from disk and *repopulates* the
  RAM var.

### 2.3 On-disk source of truth
`/storage/emulated/0/Download/AAB/configs/contexts.json` — a JSON **array** of context objects.
task623 writes it **atomically** (write `.tmp` → `renameTo`, with delete+rename fallback).

Context object schema (from task43 PASS 3 reader + task623 writer):
```json
{
  "id":       "<stable unique id>",          // key for SAVE/DELETE upsert
  "name":     "Cinema",                       // display + log
  "profile":  "Movie",                        // profile FILE name to load (configs/<profile>.json)
  "priority": 10,                             // precedence integer (default 0)
  "triggers": {
    "apps":       ["com.netflix.mediaclient", ...],   // optional
    "wifi":       ["HomeNet", "Office"],              // optional, trimmed compare
    "battery":    { "min":0, "max":20, "on_power":true }, // any subset optional
    "location":   { "lat":51.5, "lon":-0.1, "radius":150 },// metres
    "time_range": ["22:00", "SUNRISE"],              // [start,end]; "HH:MM" | "SUNRISE" | "SUNSET"
    "days":       [1,2,3,4,5,6,7]                     // Calendar.DAY_OF_WEEK, 1=Sun..7=Sat
  }
}
```

### 2.4 Daily reset (prof8 → task26 `_ResetContextCacheDaily`)
- Fires **03:00 daily** (Time con0 `fh=3 fm=0 th=3 tm=0`) while `%AAB_ContextCache` isSet.
- task26 is a single action **code 549 (Variable Clear)** on **`%AAB_ContextJSONCache`** (arg1=0
  pattern-match off). It does **not** touch `%AAB_ContextCache` or the disk file.
- Effect: invalidates the RAM JSON copy so the next task43 run reloads `contexts.json` from disk
  (a failsafe against a stale/corrupt RAM cache; label says exactly this). SUNRISE/SUNSET solar
  values are re-read fresh each eval anyway, so this mainly guards cache integrity.

---

## 3. `_ContextManager` (task623) — the writer/cache-builder

`par1` = command, `par2` = JSON payload.
- **SAVE_CONTEXT**: parse `par2` as one object, upsert into the array by `id` (replace if id
  matches, else append). `context_status="Saved"`.
- **DELETE_CONTEXT**: `par2` = id string; rebuild array excluding that id. `context_status="Deleted"`.
- Always: atomic write to disk (§2.3), then rebuild **both** caches (§2.1, §2.2).
This is the single mutation entry point; the UI (task625 `_AppPicker`, task624 `_GetLocationForContext`,
task633 `_GetWifiForContext`) only *gathers* inputs for the editor.

Input-gathering helpers (no override logic, just data for the rule editor):
- **task625 `_AppPicker`** — enumerates launchable installed apps → JSON `[{n,p,i}]` (name,
  package, 48×48 base64 PNG icon) into `%app_list_json`.
- **task624 `_GetLocationForContext`** (pri 100) — `Get Location` (code 366, 30 s/300 m timeout)
  → feeds `setLocationInput(lat,lon)` into the WebView editor.
- **task633 `_GetWifiForContext`** — reads current SSID (strips quotes, rejects `<unknown ssid>`/
  `<redacted>`) → `%current_ssid` for the editor.

---

## 4. `_EvaluateContexts V2` (task43) — the decision engine

Single Java action (code 474) at `arg0`, wrapped by Tasker actions that (a) collect inputs
before it and (b) apply the result after it.

**Tasker wrapper, pre-Java** (collect ambient inputs): `Get Net Location` → `_GetWifiForContext`
→ `_GetWifiNoLocation V3` (task105: privilege-aware SSID/`%bypass_ssid` fetch; also auto-detects
Root/Shizuku/ADB-WiFi secondary privilege and caches in `%AAB_SecondaryPrivilege`). These set
`%app_package`, `%net_location`, `%BATT`, `%bypass_ssid` that the Java reads.

**Java, 4 passes:**

**PASS 1 — inputs + dynamic cooldown.** Per-caller cooldown debounce (global, keyed on
`%AAB_LastEvalTime`):
| caller contains | cooldown |
|---|---|
| `_ContextResume` | 0 ms (immediate) |
| `Battery` | 30 000 ms |
| `Location` | 8 000 ms |
| `Wifi`/`WIFI` | 8 000 ms |
| `Time` | 1 000 ms |
| else (App Changed/general) | 500 ms |
Then reads solar (`%AAB_Sunrise/Sunset`, UTC seconds → shifted to local), de-serializes
`%AAB_ContextState` (`app#lat#lon#batt#plug#day#wifi`), and gathers current app, net location,
battery %, plugged status (live `BATTERY_CHANGED` receiver), day-of-week, and current SSID
(`%bypass_ssid` first, else WifiManager).

**PASS 2 — veto gates** (skip eval if nothing relevant changed). Always-eval on midnight
rollover (`curDay != lastDay`) or `_ContextResume`. Otherwise per-caller:
- App Changed: eval only if rule active OR non-default profile OR `cache.contains(","+app+",")`,
  AND app actually changed.
- Location: eval only if cache has `[LOC]`.
- Battery: eval only if `[BATT]` AND (plug flipped OR |Δbatt| ≥ 5%).
- Wifi: eval only if `[WIFI]` AND SSID changed.
On proceed, write new `%AAB_ContextState`.

**PASS 3 — match + rank.** Load rules (RAM `%AAB_ContextJSONCache` → disk fallback). For each
rule, `isMatch` AND over every present trigger:
- **time_range/days**: resolves `SUNRISE`/`SUNSET`/`HH:MM` to seconds-of-day; supports
  overnight ranges (start>end spills into yesterday with prev-day membership); collects all
  start/end into `wakeTimes` for scheduling.
- **apps**: `curApp` ∈ list. **battery**: `min ≤ curBatt ≤ max` and `on_power == isPlugged`.
- **location**: `Location.distanceBetween ≤ radius`. **wifi**: `curWifi` ∈ list (trimmed).
- **specificity** = count of trigger dimensions matched (time, +1 if days, apps, battery, loc, wifi).

**Precedence / conflict resolution (the real "matrix"):** among all matching rules, winner =
**highest `priority`**; ties broken by **highest specificity**; remaining ties keep the
first-seen (array order). `priority` defaults to 0 if absent.

| Rank key | Field | Tie-break |
|---|---|---|
| 1 (primary) | rule `priority` (int, default 0) | higher wins |
| 2 (tie-break) | `specificity` (# trigger dims) | higher wins |
| 3 (final tie) | array order in contexts.json | first wins |

**PASS 4 — output.**
- If **no manual context override** (`%AAB_ContextOverride != "true"`):
  - winner found → `target_context_profile = winner.profile`, `%AAB_ActiveContext = name`.
  - no winner → `%AAB_ActiveContext = null`, fall back to `%AAB_ProfileUser` (or `Default` if
    its `<profile>.json` is missing, also resetting `%AAB_ProfileUser=Default`).
- If override active → **skip the profile switch** entirely (only refresh wake times); log
  "Override Active … Profile switch skipped."
- Compute `%AAB_NextContextTime` = nearest future `wakeTimes` entry as `HH.MM` (drives prof764),
  or null if no time rules.

**Tasker wrapper, post-Java** (apply): act17 guards — **skip apply if** `target_context_profile`
unset, OR equals current `%AAB_CurrentActiveProfile`, OR caller is `_ContextResume`. Otherwise
act18 sets `%AAB_CurrentActiveProfile = target_context_profile`, act19 calls **`_ProfileManager
LOAD_FILE %target_context_profile`**, updates the dashboard UI (act20), and act21 re-runs **`Set
Initial Brightness (Java) V3`**.

### What an override actually CHANGES
It **swaps the entire active brightness profile**, not a single knob. `_ProfileManager LOAD_FILE`
loads `configs/<profile>.json`, which (per `_ContextResume` key list, task626) repopulates the
full parameter set: the 3-zone curve coefficients (`Form1A/2A/2B/2C/3A`, `Zone1End/Zone2End`),
`MinBright`/`MaxBright`, `Scale`/`Offset`, animation (`AnimSteps/MinWait/MaxWait/DeltaFactor`),
thresholds (`ThreshDark/Dim/Bright/Steepness/Midpoint`), dynamic-scale params, dimming params,
PWM params, etc. So an override does not merely "scale" or "clamp min/max" — it **replaces the
whole curve+behaviour profile** with another saved profile. Reverting to no-match restores
`%AAB_ProfileUser` (the user's default profile).

---

## 5. Location subsystem (prof765/766/767 + task630/631)

- **task630 `_ContextLocnListener V4`** (prof765 enter): registers a persistent
  `LocationListener` (fused→network→gps, 3 min / 100 m). On callback: writes heartbeat
  `%AAB_ListenerHeartbeatTMS`, resets `%AAB_LocnBackOff=0`, filters accuracy >500 m, and updates
  `%AAB_NetLocation`/`%AAB_NetLocationTMS` only on first fix or ≥100 m displacement. Stores the
  listener/manager as Java globals; torn down on prof765 exit (mid1=630).
- **task631 `_ContextF5NetLoc V8`** (prof766, every 3 min): watchdog + adaptive cache refresher.
  Early-exits if Location services off (sets `should_stop`). Checks listener health (detects
  **zombie listeners** via displacement when heartbeat >30 min old), Wi-Fi connection, movement
  speed (>5 m/s), and power-save. Computes an adaptive **cache-staleness tier**
  (BLIND 220 s / ANCHORED 1800 s / ROAMING 200–240 s) with exponential **backoff** (×2^level,
  cap 3600 s; +3 levels under power-save). Decides `needs_active_poll`; if cache fresh, publishes
  `%cached_location`, else flags an active GPS poll.
- New `%AAB_NetLocation` writes fire **prof767** (Variable Set 3050) → task43 with a Location caller.

---

## 6. Six-line summary

1. Precedence is over **per-rule `priority` (int)** in contexts.json — NOT profile `<pri>` (all
   context profiles are pri 0); ties broken by trigger **specificity**, then array order.
2. Eight watcher profiles (762–768 app/batt/time/loc×3/wifi + prof8 daily reset) gate cheaply on
   `%AAB_Service==On`, `%AAB_Manual_Override!=true`, `%AAB_ContextOverride!=true`, and a
   `%AAB_ContextCache` signal token, then call `task43 _EvaluateContexts V2`.
3. `task43` debounces (per-caller cooldown), vetoes unchanged signals, matches every rule's
   app/wifi/battery/time-range+days/location triggers, and picks the highest-priority match.
4. An override **swaps the entire active profile** via `_ProfileManager LOAD_FILE` — replacing
   the full curve/min-max/scale/threshold/dimming parameter set — then re-runs Set Initial
   Brightness; no match reverts to `%AAB_ProfileUser` (else Default).
5. Two caches: `%AAB_ContextCache` (`[BATT][LOC][WIFI],pkg,pkg,` token+app gate) and
   `%AAB_ContextJSONCache` (full RAM JSON), both rebuilt by `_ContextManager` on save/delete;
   disk truth = `Download/AAB/configs/contexts.json` (atomic write).
6. prof8 at 03:00 daily clears only `%AAB_ContextJSONCache` (task26, code 549), forcing a fresh
   disk reload; `_ContextResume` (task626) snapshots/restores the live profile var set and runs
   task43 with zero cooldown and forced eval.
