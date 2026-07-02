# XML_RECIPES — verified access patterns for Advanced_Auto_Brightness_V3.3.prj_9.xml

The Tasker export `Advanced_Auto_Brightness_V3.3.prj_9.xml` lives at
`docs/rebuild/extraction/_source/` (gitignored since S12.9a — owner picks LFS-vs-ignore at PR review)
and is 1.6 MB / 41,291 lines. **NEVER read it wholesale** (it would consume your entire context window).
Every recipe below was executed and verified on 2026-06-11 against this exact file. `$X` means the XML path.

```bash
X=docs/rebuild/extraction/_source/Advanced_Auto_Brightness_V3.3.prj_9.xml
```

## R0 — Restoring the XML in a fresh clone (`_source/` is gitignored since S12.9a)

A fresh checkout has no `$X` — but the file lives in git HISTORY (it was committed before the
ignore). Restore it locally (stays untracked) and sanity-check with the R1 censuses:

```bash
git rev-list --all --objects | grep prj_9   # → blob 17e3ff6ed6ecb4a30636a7fd1712c8be53547a31
git cat-file blob 17e3ff6ed6ecb4a30636a7fd1712c8be53547a31 > $X
grep -c '<Profile sr=' $X                   # must print 18
```

(Verified 2026-07-02, F-backlog U5: restored file passes every R1 census — 41,291 lines,
18 profiles, 276 tasks, 40 Java blocks.)

## R1 — Censuses (use these to sanity-check your extraction is complete)

```bash
grep -c '<Profile sr=' $X    # => 18
grep -c '<Task sr=' $X       # => 276   (Task elements; 108 carry <nme> names — S3.5 verified; the old "~159" counted ALL <nme> in the file incl. profiles/scenes)
grep -c '<Scene sr=' $X      # => 20
grep -c '<Action sr=' $X     # => 2274
grep -c '<code>474</code>' $X  # => 40  (embedded Java blocks — see R7)
grep -o '%AAB_[A-Za-z0-9_]*' $X | sort -u | wc -l   # => 125 distinct project variables
```

## R2 — Extract one task by id (verified: task535 => 64 lines)

```bash
awk '/<Task sr="task535">/,/<\/Task>/' $X
```

Task elements have NO attributes after the sr, so the closing `>` in the pattern is safe.
Pipe long tasks through `head -c`/`wc -l` first to size them before dumping into context.

## R3 — Task id/name census

```bash
grep -A6 '<Task sr=' $X | grep -E '<(id|nme)>'
```

Emits `<id>` and (when present) `<nme>` pairs in file order. Unnamed Task elements are
anonymous scene handlers (168 of them — see R11 + `extraction/tasks/anonymous_handlers.md`);
the 108 named tasks are the by-name inventory (S3.5-corrected count; was misstated as ~159).

## R4 — Profiles. ⚠️ Profiles carry a `ve="2"` attribute — do NOT close the pattern with `>`

```bash
awk '/<Profile sr="prof760"/,/<\/Profile>/' $X      # correct
# awk '/<Profile sr="prof760">/,...'                # WRONG — matches nothing
```

Profile block line anchors (verified): prof753 L3, prof754 L16, prof755 L56, prof756 L111,
prof757 L156, prof758 L195, prof759 L300, prof760 L318, prof761 L386, prof762 L398,
prof763 L456, prof764 L500, prof765 L541, prof766 L579, prof767 L628, prof768 L676,
prof769 L722, prof8 L744. (All 18 live in lines 3–798, before the scenes.)

Inside a profile: `<id>`, `<nme>`, `<mid0>` = enter task id, `<mid1>` = exit task id,
`<pri>` = priority, then context children (`<Event sr="con0">`, `<State ...>`, `<App ...>`,
`<Time ...>`, `<Day ...>`) each with a `<code>` (context type) and args.

**⚠️ ConditionList children are stored ALPHABETICALLY, not numerically** (`bool10`/`bool11`/`bool12`
sort before `bool2`; `c10` before `c2`). Re-sort by numeric index before reading a gate, or the boolean
sequence comes out scrambled — this mis-transcribed prof758 in S1 (fixed S3.5, D-021). Boolean
semantics (owner-validated, D-021): plain `And`/`Or` bind tighter (inner groups, `And` > `Or`);
`And2`/`Or2` join those groups left-to-right.

**⚠️ Profile-level `<ConditionList sr="if">` blocks are load-bearing pipeline logic.**
Example: prof760 "Monitor Ambient Light" (Event code 2088 = Sensor, type 5 = light, arg2=1000ms)
gates on `%as_values1` vs `%AAB_ThreshAbsLow`/`%AAB_ThreshAbsHigh`, `%AAB_TrustUnreliable` vs
`%as_accuracy`, and `%AAB_MainLoop` — i.e. the absolute-threshold gate runs BEFORE the enter
task fires. Extraction that only reads enter tasks will miss this.

## R5 — Scenes (verified line anchors)

```bash
grep -n '<Scene sr=' $X
```

| Scene | Line |
|---|---|
| AAB About | 799 |
| AAB Alpha Graph | 1038 |
| AAB Brightness Graph | 1202 |
| AAB Brightness Settings | 1415 |
| AAB Chart.Js License | 2194 |
| AAB Circadian Dimming Graph | 2388 |
| AAB Color Filter | 2552 |
| AAB Debug Scene | 2583 |
| AAB Dimming Graph | 3006 |
| AAB Experiment Graph | 3170 |
| AAB Experiment Settings | 3334 |
| AAB Menu | 4462 |
| AAB Misc Settings | 4718 |
| AAB Power Draw Graph | 5611 |
| AAB Profile | 5724 |
| AAB Reactivity Graph | 6563 |
| AAB Reactivity Settings | 6739 |
| AAB Superdimming Settings | 7533 |
| AAB Taper Graph | 8387 |
| AAB User Guide | 8551 |

Scene names contain spaces and dots — extract by line window (`sed -n '1415,2193p' $X`) or
`awk '/<Scene sr="sceneAAB Brightness Settings"/,/<\/Scene>/'`. Scenes end at the next
`</Scene>`; the last scene ends before the first `<Task sr=` block (~L8560+).
Element histogram across all 20: 224 RectElement, 129 TextElement, 28 WebElement,
22 EditTextElement, 20 PropertiesElement, 16 SwitchElement, 6 SliderElement, 5 ButtonElement.

## R6 — Window peek by line numbers

```bash
sed -n '15204,15260p' $X
```

Use after `grep -n` to inspect a region. Keep windows ≤ 150 lines.

## R7 — The 40 embedded Java blocks (action `<code>474</code>`)

**Java Code actions are `<code>474</code>`. NOT 598** (598 = Variable Search/Replace; it also
appears 35× — an earlier audit confused the two). The Java source is in `<Str sr="arg0">`,
XML-entity-encoded (`&lt; &gt; &amp; &quot;`) — decode before saving. `arg1` = output variable.

Census mapping (verified line → enclosing task; note several tasks have TWO blocks):

| Line | Task | Name |
|---|---|---|
| L8906 | task105 | _GetWifiNoLocation V3 |
| L9468 | task378 | _PrivilegeDetection V5 (Java) |
| L9921 | task38 | _SuggestCurveParameters V24 (Hybrid) |
| L12091 | task43 | _EvaluateContexts V2 |
| L14246 | task524 | _CalibratePowerDraw |
| L15204 | task535 | Lux Smoothing (Java) |
| L15878 | task543 | Calculate Animation (Java) V2 |
| L16062 | task544 | Evaluate Light Change (Java) V2 |
| L16481 | task546 | Set Thresholds (Java) |
| L16630 | task548 | Dynamic Range Compressed Scale (Java) V2 |
| L17138 | task549 | _GenerateCircadianGraph V8 (Java) |
| L18132 | task554 | Process Sensor Event (Java) |
| L18359 | task556 | _GenerateDimmingCurveGraph (Java) |
| L18959 | task557 | _GenerateAlphaGraph (Java) |
| L19677 | task563 | _AskPermissionsV7 |
| L24132 | task592 | _CreateDefaultProfiles |
| L25826, L26096 | task618 | Set Initial Brightness (Java) V3 (×2) |
| L26400 | task620 | _AdaptiveBrightnessSceneSize V4 |
| L26926 | task623 | _ContextManager |
| L27185 | task625 | _AppPicker |
| L27355 | task626 | _ContextResume |
| L27585, L27817 | task630 | _ContextLocnListener V4 (×2) |
| L27939, L28432 | task631 | _ContextF5NetLoc V8 (×2) |
| L28827 | task633 | _GetWifiForContext |
| L28993 | task636 | _DeleteOverridePoint |
| L29303 | task637 | _ProfileManager |
| L30505 | task643 | _LearnWriteSecure |
| L32591 | task655 | _SetSuggestedVariables |
| L32986 | task657 | _GenerateCompressionGraph (Java) |
| L33944, L34370 | task663 | _GenerateGraph (Java) (×2) |
| L35733 | task696 | Smooth Brightness Transition V5 (Java) |
| L36043 | task698 | Smooth DC-Like Brightness Transition V5 (Java) |
| L36847 | task703 | _GenerateReactivityGraph (Java) |
| L37517 | task705 | _GenerateCircadianDimmingGraph (Java) |
| L40429, L41085 | task90 | Dynamic Scale V13 (Java) App Version (×2) |

Reusable census command (regenerates the table above):

```bash
python3 - <<'EOF'
import re
lines = open('docs/rebuild/extraction/_source/Advanced_Auto_Brightness_V3.3.prj_9.xml', encoding='utf-8').read().splitlines()
task_re, nme_re = re.compile(r'<Task sr="task(\d+)">'), re.compile(r'<nme>([^<]*)</nme>')
cur, name, pending = None, None, False
for i, l in enumerate(lines, 1):
    m = task_re.search(l)
    if m: cur, name, pending = m.group(1), None, True; continue
    if pending:
        n = nme_re.search(l)
        if n: name, pending = n.group(1), False
    if '<code>474</code>' in l: print(f"L{i}\ttask{cur}\t{name}")
EOF
```

**⚠️ task661 "Map Lux to Brightness (Java) V2" has NO Java block** despite its name — its 32
actions are Tasker `Variable Set` (code 547, supports maths expressions), `Perform Task` (130),
If/Else, one built-in `Set Display Brightness` (810). The 3-zone curve formula in *Java* form
exists inside graph generator task663 (~L34405) as a plot-side reimplementation — use it to
cross-validate the runtime math extracted from task661's actions, not as its source.

**⚠️ `%var` substitution semantics:** Tasker textually substitutes variable VALUES into the Java
source before compiling (e.g. `double par1 = %par1;` becomes `double par1 = 42.0;`). When
transcribing to a reference implementation, each `%X` becomes a function parameter.

## R8 — Action-code histogram inside a task

```bash
awk '/<Task sr="task661">/,/<\/Task>/' $X | grep -o '<code>[0-9]*</code>' | sort | uniq -c | sort -rn
```

Frequent codes in this project (derive others from context/`<label>` hints, record unknowns —
do not guess): 547 Variable Set · 37 If · 38 End If · 43 Else/Else-If · 130 Perform Task ·
548 Flash · 549 Variable Clear · 474 Java Code · 598 Variable Search/Replace · 30 Wait ·
137 Stop · 810 Set Display Brightness · 235 Custom Setting · 779(?) shell-adjacent ·
523 Notify · 159/165(?) — verify in place.

## R9 — Whole-file pattern hunting

```bash
grep -n 'reduce_bright_colors' $X     # super-dimming secure setting writes
grep -n 'Settings.System' $X          # direct settings API usage in Java blocks
grep -n 'Shizuku' $X                  # privilege escalation path (14 hits)
grep -n 'pm grant' $X                 # adb grant instructions
```

## R10 — prj_4 (older revision, for archaeology only)

```bash
git show 9d36d36^:Advanced_Auto_Brightness_V3.3.prj_4.xml | grep -c '<Task sr='
```

prj_9 ≈ prj_4: only `_SuggestCurveParameters` bumped V23→V24 (task id 41→38). Codex-era
docs in docs/migration/ were derived from prj_4 and are therefore NOT stale, just shallow.

## R11 — Anonymous (unnamed) tasks → scene handlers (S3.5)

168 of 276 tasks have no `<nme>` — all are scene-element handlers (`<clickTask>`, `<checkchangeTask>`,
`<valueselectedTask>`, `<keyTask>`, …) referenced by id from `<Scene>` blocks. Census + wiring +
action summaries: `extraction/tasks/anonymous_handlers.md`. Find the callers of task N:

```bash
grep -n ">N</" $X | grep -v "<id>"     # e.g. <clickTask>517</clickTask>
```

Extract the body with R2. Named tasks are never wired by id from scenes (they are invoked by name via
Perform Task / scene-HTML `performTask('Name', pri)`).
