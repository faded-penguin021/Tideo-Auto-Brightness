# INDEX.md — S1 extraction inventory, action-code legend, unresolved, spot-checks

S1 = "Ground-truth extraction A: core pipeline + all 40 Java blocks". All artifacts below were
extracted from `Advanced_Auto_Brightness_V3.3.prj_9.xml` via `XML_RECIPES.md` (never read wholesale).
Java blocks decoded with Python `html.unescape`. Provenance is stamped in every file.

## Inventory

| Deliverable | Path | Count |
|---|---|---|
| Embedded Java blocks (verbatim, decoded) | `extraction/_source/java/task<id>_<n>_<slug>.java.txt` | **40** (matches R7 census) |
| Pipeline/feature task transcriptions | `extraction/tasks/task<id>_<slug>.md` | **28** |
| Pipeline-triggering profiles | `extraction/profiles.md` | 10 (prof753,754,755,756,757,758,759,760,761,769) |
| End-to-end pipeline spec | `extraction/pipeline_spec.md` | 1 |
| 125-variable defaults audit | `extraction/defaults_audit.md` | 1 (125 vars) |
| This index | `extraction/INDEX.md` | 1 |

### 40 Java blocks (task · block# · `<code>474</code>` line)
105·1 L8906 · 378·1 L9468 · 38·1 L9921 · 43·1 L12091 · 524·1 L14246 · 535·1 L15204 · 543·1 L15878 ·
544·1 L16062 · 546·1 L16481 · 548·1 L16630 · 549·1 L17138 · 554·1 L18132 · 556·1 L18359 · 557·1 L18959 ·
563·1 L19677 · 592·1 L24132 · 618·1 L25826 · 618·2 L26096 · 620·1 L26400 · 623·1 L26926 · 625·1 L27185 ·
626·1 L27355 · 630·1 L27585 · 630·2 L27817 · 631·1 L27939 · 631·2 L28432 · 633·1 L28827 · 636·1 L28993 ·
637·1 L29303 · 643·1 L30505 · 655·1 L32591 · 657·1 L32986 · 663·1 L33944 · 663·2 L34370 · 696·1 L35733 ·
698·1 L36043 · 703·1 L36847 · 705·1 L37517 · 90·1 L40429 · 90·2 L41085.
(38 of 40 write via `tasker.setVariable()` and have no `arg1`; the 2 with an `arg1` output var are
task535 → `%output` and task548 → `%dr_results`.)

### 28 task transcriptions
554, 544, 535, 546, 661, 548, 659, 543, 696, 698, 700, 618, 585, 566, 567, 569, 561, 528, 570, 645,
646, 647, 650, 653, 654, 644, 551, 584 — each as `tasks/task<id>_<slug>.md`.

## Action-code legend (derived from in-place evidence in this project)

| Code | Meaning | Confidence / evidence |
|---|---|---|
| 30 | Wait | arg0 = seconds/ms (task567 act7 `%AAB_CycleTime`) |
| 37 / 38 / 43 | If / End If / Else(-If) | structural, ubiquitous |
| 49 | Hide/Destroy Scene | `code49 arg0="AAB Color Filter"` (task585 act14) |
| 123 | Run Shell | `settings put secure reduce_bright_colors_activated 0` (task567 act25) |
| 126 | Return | task535/543/659 final action |
| 130 | Perform Task | arg0=name, arg1=priority, arg2=par1, arg3=par2, arg4=return var |
| 135 | Goto (label) | loops to `arg2` label (task561 act15 → `top_of_loop`) |
| 137 | Stop | ubiquitous guards |
| 159 | Profile Status (enable/disable named profile) | arg0=profile name, arg1=1 enable (task570/585/567) |
| 235 | Custom Setting | R8 hint |
| 300 | Anchor / loop label | labeled `top_of_loop` (task561 act11) |
| 355 | Array Push (value at index) | `%AAB_Overrides` push (task561 act6/9) |
| 356 | Array element delete | `%AAB_Overrides` delete at index (task561 act14) |
| 389 | Multiple Variables Set | newline-separated `name=value` block (task544 act2/14/27) |
| 105 | Set Clipboard (S3.5, owner-corrected) | single use: `%AAB_Test` → clipboard (L9864, curve-wizard report — D-025) |
| 474 | Java Code | arg0 = source (entity-encoded), arg1 = optional output var |
| 523 / 779 | Notify / Notify Cancel(by title) | task567 act19 notify; task567/569 cancel |
| 547 | Variable Set | arg0=name, arg1=value; arg2=Recurse, **arg3=DoMaths**, arg4=Append |
| 548 | Flash | arg0=text |
| 549 | Variable Clear | arg0=name |
| 590 | **Variable Split** (S3.5, owner-corrected — was mislabeled "Array Push") | arg0=var, arg1=separator (`,`), arg2=delete-base; e.g. `%lux_results` split (task544 act26); 9 uses |
| 598 | Variable Search/Replace | R7 (NOT Java — see D-001) |
| 810 | Set Display Brightness | arg0=brightness (task661 act45) |

**Context (profile) codes:** 208 Display On · 210 Display Off · 125 Proximity · 2088 Sensor (light, type 5)
· 2075 Setting changed (`screen_brightness`) · 165 State/Time (periodic) · 123 State · 2083 panic trigger.

Condition `<op>` codes (project-wide histogram): `0 ~` · `1 !~` · `2 =` · `3 !=` · `6 <` · `7 >` ·
`9 >=` · `12 isSet` · `13 isNotSet` (`4/5/8/10` not observed in the owned scope).

## Unresolved (recorded, NOT guessed)

1. **RESOLVED (S3.5, owner-verified → D-021).** `And2`/`Or2` grouping: plain `And`/`Or` bind tighter
   (inner groups, `And` > `Or`); `And2`/`Or2` join those groups left-to-right. prof760's reading
   confirmed by the owner (incl. the `%AAB_MainLoop != On` mutex polarity); prof758 re-derived in
   `profiles.md` — whose bool sequence was also FIXED there (⚠️ ConditionList children are stored
   ALPHABETICALLY in the XML; re-sort numerically — see R4). task551 act0 reading updated in
   `features_spec.md`. Residual: Gate-1 runtime sanity check only.
2. **RESOLVED (S3.5, owner-confirmed → D-025).** `%AAB_MaxSteps` is legacy — the abandoned predecessor
   of `AAB_AnimSteps`. Do not port; do not invent a default.
3. **Context subtype codes 165 (State vs Time), 123** — best-guess given (periodic/state); exact Tasker
   context subtype IDs not cross-referenced to an external table. Effect is unambiguous from the gate +
   enter task; only the raw code label is uncertain. **2083 resolved (S3.5, owner):** prof769's trigger
   is the significant-motion/shake event; its companion State 120 arg0=3 = Orientation "upside down"
   (123 arg0=1 label still inferred).
4. **Action codes 49, 135, 300, 355, 356** — meanings inferred from in-place evidence (hide-scene, goto,
   loop anchor, array push, array delete), not from a canonical Tasker code table. High confidence but
   flagged. **389 = Multiple Variables Set** is confident.
5. **task554 act2 Perform-Task `par2`** and similar empty `<Str .../>` args are correctly empty (the
   transcriber was fixed to stop at `</Action>`); no residual ambiguity.

## Spot-check (3 formulas, XML provenance, vs `domain/.../brightness/BrightnessEngine.kt`)

> Read-only comparison per S1 brief — **do NOT fix code here** (S4/S5 own parity).

1. **Lux-smoothing alpha** — Tasker task535 `java/task535_1` (XML L15204):
   `lux_delta=round3(|p1−p2|/(p2+1))`; `effective_delta=round3(lux_delta−ThreshDynamic/100)`;
   `lux_alpha=round3(1−exp(−DeltaFactor·effective_delta))`.
   Engine `luxSmoothing()` L90–92: **MATCHES** the three round3 steps exactly.
   ⚠️ *Minor divergence:* Engine adds `.coerceIn(0.0,1.0)` to `luxAlpha`; Tasker task535 does **not** clamp
   alpha. (S4 golden vectors will surface effective-delta-negative tie rows.)

2. **3-zone curve** — Tasker task661 acts 3–9 (XML L33633) + derived coeffs task659 (L33331):
   z1 `Form1A·sqrt(lux)`; z2 `Form2A+Form2B·((lux−Form2C)^0.33−(Form2D−Form2C)^0.33)`;
   z3 `MaxBright−(Form3A/lux)·MaxBright`.
   Engine `mapLuxToBrightness()` L114–121: **MATCHES** (Engine uses `zone1End` where Tasker uses `Form2D`,
   but `Form2D=Zone1End` by task570 act18 / task659, so equivalent).
   ⚠️ *Minor divergence:* Engine wraps the `^0.33` bases in `.coerceAtLeast(0.0)` (NaN guard); Tasker does
   not (would yield NaN for negative base). Confirm boundary behavior in S4.

3. **Dynamic threshold (sigmoid + dark branch)** — Tasker task544 `java/task544_1` A17–A23 (XML L16062):
   `thresh_sig=round3(ThreshDim+(ThreshBright−ThreshDim)/(1+exp(−ThreshSteepness·(log10(lux+1)−ThreshMidpoint))))`;
   `thresh_low=round3(ThreshDark−((ThreshDark−ThreshDim)/Zone1End)·lux)`; pick `thresh_low` if `lux<Zone1End` else `thresh_sig`.
   Engine `computeDynamicThreshold()` L100–103: **EXACT MATCH** (both `round3`, both branches, same selector).

**Spot-check verdict:** core math already conforms; only two engine-side defensive clamps
(`luxAlpha.coerceIn`, `pow base.coerceAtLeast`) differ from raw Tasker — logged for S4/S5, code untouched.
