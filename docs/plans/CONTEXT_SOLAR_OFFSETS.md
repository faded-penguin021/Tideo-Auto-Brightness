# Sunrise/Sunset offsets for context time rules (AAB `_EvaluateContexts` revision)

## Status

Spec only. No code has changed. The approved action is committing this plan to `docs/plans/`
(owner, 2026-10-01). Executing either unit needs the owner's separate go-ahead. Even then, only
session-branch commits are authorised: no PR, tag or release.

## Context

The owner added a feature to AAB (the Tasker project) and pasted two sources on 2026-10-01: the
revised `_EvaluateContexts` Java (task43, action A12) and the Profile Manager webview. The changed
regions are quoted verbatim in the appendices below, which are the only copy that survives this
session (owner's choice).

Compared with the tracked transcript `docs/rebuild/extraction/_source/java/task43_1_evaluatecontexts-v2.java.txt`,
all the behavioural changes are in time ranges:

1. **Solar offsets.** A `time_range` endpoint may be `SUNRISE±N` or `SUNSET±N`, with N in minutes.
   - The endpoint is trimmed and matched with `startsWith("SUNRISE"|"SUNSET")`.
   - The offset is read after the first `+`, otherwise after the first `-` (negated). A parse
     failure counts as 0.
   - The result is wrapped into 0..86399 and **floored to the minute**. Plain `SUNRISE`/`SUNSET` are
     floored too.
2. **Inclusive-minute matching.** The window test uses `nowMinute` (H·3600 + M·60, with seconds
   dropped) instead of `nowSecs`, so a rule stays active for all of its end minute.
3. **Exit wake at end + 60 s.** The wake times are `start` and `(end + 60) % 86400`. The old code
   woke at `end`, where `nowSecs <= end` still matched, so the rule did not exit until some later
   evaluation. The next-wake calculation itself still uses exact `nowSecs`.
4. **Editor (webview).** Picking Sunrise or Sunset reveals an "Offset (min)" field ("e.g. +30 or
   -15").
   - A live preview shows the resolved local time (base + offset, floored to the minute) in yellow.
   - A blank offset means 0.
   - Save is blocked unless the offset matches `^[+-]?\d+$` ("Invalid Offset").
   - The saved token is `SUNRISE+30`, `SUNSET-15`, or bare `SUNRISE` when the offset is 0.
   - Editing a rule seeds the field from its token (`+30`/`-15`).
   - Picking a clock time clears the offset; switching between Sunrise and Sunset keeps it.

Everything else in both pastes is cosmetic (listed in Appendix A).

**Today's failure.** Tideo's `ContextMatching.resolveTimeToken` treats anything other than an exact
`SUNRISE` or `SUNSET` as `HH:MM`. A `SUNRISE+30` imported from AAB therefore throws
(`split(":")[1]` is out of bounds) and aborts the evaluation.

Tasker semantics override coding taste (AGENTS.md). The domain change follows RUNBOOK playbook 2
(task43 changed), and the editor change follows playbook 3 (Profile scene changed).

## Execution rules

- Units run in order. Each ends with `scripts/ladder.sh` green, then a STATE Changelog line, then a
  commit and a push to `session/gracious-clarke-0f56my`. Commit bodies say what ran and what could
  not be checked on a device.
- The phone over adb is read-only. The owner runs the device checks (`DEVICE_TEST_SCRIPT.md`).
- Neither unit touches `:platform` or the runtime glue (`ContextEngine`, `AndroidContextSignalSource`),
  so the glue-review protocol does not apply. If either does need to change, run the protocol.
- **Train: 1.13.0 / vc27, shared with the #136 plan** (owner, 2026-10-01). v1.12.0 is tagged. Whichever
  unit from the two plans lands first does the bump and opens `changelogs/27.txt` (500-character cap).
  The plugged-in fix (DD-025) has since done both: add a sentence to `27.txt` instead.

## U1 — Evaluator parity (`:domain`)

**New public, pure helper: `domain/.../context/SolarTimeTokens.kt`.** It is the single home of the
token grammar, so the editor preview and the engine cannot disagree.

- `eventOf(token): String?` returns `"SUNRISE"` or `"SUNSET"` by a trimmed `startsWith`, testing
  SUNRISE first as Tasker does. Otherwise it returns null.
- `offsetMinutes(token): Long` is the evaluator's parse, with its quirks kept:
  - after the first `+`: `toLongOrNull() ?: 0`;
  - otherwise after the first `-`: `-(toLongOrNull() ?: 0)`;
  - so `SUNRISE++5` gives +5, `SUNRISE--5` gives +5, and `SUNRISEx` gives 0.
- `resolve(baseLocalSecs, offsetMinutes): Long` = `floorMod(base + off·60, 86400) / 60 · 60`.
- `editorOffsetText(token): String` is the webview's edit-mode seed:
  - `"+" + after('+').trim()`;
  - otherwise `"-" + after('-').trim()`;
  - otherwise `""`.
- `commit(event, offsetText): String?` builds the saved token:
  - blank → `event`;
  - fails `^[+-]?\d+$` or overflows `Long` → null (invalid);
  - otherwise >0 → `EVENT+n`, <0 → `EVENT-n`, 0 → `EVENT`.

**`ContextMatching.kt`:**
- `resolveTimeToken` hands solar endpoints to `SolarTimeTokens`. The `HH:MM` path is unchanged, and
  it still trims each part.
- `timeDayWindowMatches` uses `nowMinute = nowSecondsOfDay / 60 * 60` in all four comparisons.
- `nextWakeTime` is unchanged and keeps exact seconds.

**`ContextOverrideResolver.kt`:** `wakeTimes.add((end + 60) % SECONDS_PER_DAY)`.

**Comments:**
- Update the KDoc token grammar in `ContextModel.TimeRange` and `ContextTriggers.timeRange`.
- Add one `// Tasker: task43 …` marker on the solar parse.
- Keep comments to one-line pointers, to stay within the comment budget.

**Tests:** extend `ContextOverrideResolverTest` and add a new `SolarTimeTokensTest`.
- Offset parsing: `+30`, `-15`, ` + 30`, garbage → 0, `++5`/`--5`, `SUNRISEx`.
- Wrapping and flooring: offsets that cross midnight in both directions; the minute floor (a sunrise
  of 21 659 s gives 06:00).
- Inclusive end: 17:00:45 matches `09:00–17:00`; 17:01:00 does not.
- Overnight ranges that use offsets.
- Exit wake: `17.01`; an end of 23:59 wraps to `00.00`.
- Round-trips through `commit` and `editorOffsetText`.
- Expectations that change with Tasker: `overrideActive_skipsSwitch…` and `nextWakeTime_picksNearest…`
  move from `17.00` to `17.01`. These are unit tests, not golden fixtures.

**Record:**
- Copy Appendix A into `_source/java/task43_1_evaluatecontexts-v2.rev-2026-10-01.hunks.txt` with a
  header saying "owner paste, changed regions only, the rest equals task43_1 apart from the cosmetic
  list". Add a row to `INDEX.md`, and have code citations point at this file's line numbers.
- `contexts_spec.md`: §2.3 token grammar, PASS 3, the wake rule, and prof764.
- `PARITY_CHECKLIST.md`: the context row.
- `LEDGER_D.md`: a `[cited]` row (the next free DD number) for the revision and the deviations kept
  below.
- The version bump and changelog line, if this is first on the train.
- A STATE Changelog line.

## U2 — Rule editor (`:app`, where the Profile webview maps to `RuleEditor`)

**`ContextsViewModel.solarTimes()`** returns local seconds `(rise, set)` instead of strings. The UI
does the formatting, and `TimeTokenRow`'s labels look exactly as before.

**`ContextsScreen.RuleEditor`:**
- `startTime` and `endTime` hold the base value: `SUNRISE`, `SUNSET` or `HH:MM`. Seed them from
  `eventOf(token) ?: token`.
- New `startOffset`/`endOffset` text state, seeded from `editorOffsetText`.
- When a side is solar, show an `OutlinedTextField` below its token buttons:
  - label "Offset (min)", placeholder "e.g. +30 or -15";
  - input filtered to `+`, `-` and digits;
  - test tags `start_offset`/`end_offset`;
  - a gold preview of the resolved time via `SolarTimeTokens.resolve`, treating an invalid or blank
    offset as 0, as the webview does. The preview is hidden when solar times are unknown.
- Picking a time and "Clear time" both clear that side's offset. Picking a token keeps it.
- `saveRule()` commits each solar side through `SolarTimeTokens.commit`. On null it shows an inline
  error, "Offset for Start/End must be a signed whole number of minutes (e.g. +30, -15)", sets
  `isError`, and does not call `onSave`.

**Strings:** new `strings.xml` entries read through `stringResource`. `HardcodedStringCheckTest`
must not rise.

**Tests:** in `SettingsScreensTest` (Robolectric):
- choosing Sunrise shows the offset field;
- `+30` saves `SUNRISE+30` and `-15` saves `SUNSET-15`; blank or `0` saves `SUNRISE`;
- `abc` shows the error, and Save does nothing;
- editing a `SUNRISE+30` rule seeds `+30`;
- picking a time clears the offset;
- the preview shows the expected time.

**Record:**
- `scenes/profile.md` (the offset controls), `screen_map.md`, `PARITY_CHECKLIST.md`.
- A new `DEVICE_TEST_SCRIPT.md` step for the owner:
  - a clock rule ending 2 min from now exits at end + 1 min with the phone untouched;
  - a `SUNSET±N` rule whose start is about 3 min away switches on time, and its preview matches the
    switch time.
- The changelog line and a STATE Changelog line.

## Deviations kept (they go in the ledger row)

- **Solar token with no location fix.** Tideo still lets you pick one: there is no preview, and the
  engine uses 06:00/18:00, as Tasker's evaluator does. The AAB editor refuses instead ("Solar Data
  Missing"). This is existing G2R-F68 behaviour.
- **Invalid offset.** Tideo shows an inline error rather than a modal.
- **Re-tapping the selected token** does not switch back to clock time. Picking a time or "Clear time"
  does.
- **An offset that overflows `Long`** is rejected by the editor. AAB would save it and evaluate it
  as 0.
- **`HH:MM` with whitespace inside** is still accepted (each part is trimmed). Tasker would abort the
  evaluation. This leniency predates the change.

## Verification

- **U1:** `scripts/ladder.sh` (guards, `:domain:test`, build, lint).
- **U2:** the full `scripts/ladder.sh` (`:app:testDebugUnitTest`, `assembleDebug`, lint).
- **Device behaviour:** the owner runs the new `DEVICE_TEST_SCRIPT.md` step. There is no emulator
  (no KVM).

## On acceptance (this session) — the only action authorised

1. Copy this plan to `docs/plans/CONTEXT_SOLAR_OFFSETS.md`.
2. Check `git config user.email` (a no-reply address only).
3. Run `scripts/ladder.sh --guards-only`. The "plan not referenced from STATE" advisory is expected
   and deliberate, as it was for 75a6494.
4. Commit `docs: plan for AAB solar-offset context times (task43 revision)` with a body that says what
   ran, then run `git push -u origin session/gracious-clarke-0f56my`.

Nothing else happens: no code, no STATE, ledger or version change, no PR.

---

## Appendix A — `_EvaluateContexts` (task43 A12), changed regions, verbatim

**A.1** Input gathering (a new line after `nowSecs`):

```java
    cal = Calendar.getInstance();
    nowSecs = (cal.get(Calendar.HOUR_OF_DAY) * 3600) + (cal.get(Calendar.MINUTE) * 60) + cal.get(Calendar.SECOND);
    nowMinute = (cal.get(Calendar.HOUR_OF_DAY) * 3600) + (cal.get(Calendar.MINUTE) * 60);
    curDay = cal.get(Calendar.DAY_OF_WEEK); 
```

**A.2** The time-range block. It replaces transcript L314–357 (`if (hasTime) {` … `if (hasDays) specificity++;`):

```java
        if (hasTime) {
            range = triggers.getJSONArray("time_range");
            
            /* 1. Parse Start Time (Floored to Minute Boundary) */
            start = 0L;
            sRaw = range.getString(0).trim();
            if (sRaw.startsWith("SUNRISE") || sRaw.startsWith("SUNSET")) {
                baseSolar = sRaw.startsWith("SUNRISE") ? valSunrise : valSunset;
                sOffsetMin = 0L;
                if (sRaw.contains("+")) {
                    try { sOffsetMin = Long.parseLong(sRaw.substring(sRaw.indexOf("+") + 1).trim()); } catch(Exception ex){}
                } else if (sRaw.contains("-")) {
                    try { sOffsetMin = -Long.parseLong(sRaw.substring(sRaw.indexOf("-") + 1).trim()); } catch(Exception ex){}
                }
                rawSolar = ((baseSolar + (sOffsetMin * 60L)) % 86400L + 86400L) % 86400L;
                start = (rawSolar / 60L) * 60L;
            } else {
                sT = sRaw.split(":"); 
                start = (Long.parseLong(sT[0]) * 3600) + (Long.parseLong(sT[1]) * 60);
            }

            /* 2. Parse End Time (Floored to Minute Boundary) */
            end = 0L;
            eRaw = range.getString(1).trim();
            if (eRaw.startsWith("SUNRISE") || eRaw.startsWith("SUNSET")) {
                baseSolar = eRaw.startsWith("SUNRISE") ? valSunrise : valSunset;
                eOffsetMin = 0L;
                if (eRaw.contains("+")) {
                    try { eOffsetMin = Long.parseLong(eRaw.substring(eRaw.indexOf("+") + 1).trim()); } catch(Exception ex){}
                } else if (eRaw.contains("-")) {
                    try { eOffsetMin = -Long.parseLong(eRaw.substring(eRaw.indexOf("-") + 1).trim()); } catch(Exception ex){}
                }
                rawSolar = ((baseSolar + (eOffsetMin * 60L)) % 86400L + 86400L) % 86400L;
                end = (rawSolar / 60L) * 60L;
            } else {
                eT = eRaw.split(":");
                end = (Long.parseLong(eT[0]) * 3600) + (Long.parseLong(eT[1]) * 60);
            }

            /* Schedule wake at entry (start) and immediately after inclusive exit (end + 60s) */
            wakeTimes.add(new Long(start)); 
            wakeTimes.add(new Long((end + 60L) % 86400L));

            activeToday = (activeDays.isEmpty() || activeDays.contains(curDay));

            /* Evaluate matching against the current inclusive minute */
            if (start <= end) {
                if (!activeToday || nowMinute < start || nowMinute > end) timeDayMatch = false;
            } else {
                prevDay = (curDay == 1) ? 7 : curDay - 1;
                activeYesterday = (activeDays.isEmpty() || activeDays.contains(prevDay));
                matchToday = activeToday && (nowMinute >= start);
                matchYest = activeYesterday && (nowMinute <= end);
                if (!matchToday && !matchYest) timeDayMatch = false;
            }
            
            specificity++;
            if (hasDays) specificity++;
```

**A.3** Cosmetic differences from the transcript, with no behavioural effect:
- The comments `NEW: SOLAR VARIABLES` and "…to prevent crash" are shortened.
- The `(WifiManager)` cast is dropped, and the Wi-Fi, state and cache explanatory comments are
  removed.
- `String currentLog` becomes `currentLog`, and `if(isDebug)` becomes `if (isDebug)`.
- `((Long)wakeTimes.get(k)).longValue()` becomes `wakeTimes.get(k).longValue()`.
- Some blank-line layout changes.

## Appendix B — Profile Manager webview, changed regions

The logic is verbatim. Comments are condensed, wrapped lines are joined, and `style` attributes are
dropped. The CSS changes are layout-only and are omitted.

**B.1** The Start side of the time trigger. The End side is identical with `End` in place of `Start`.

```html
<input type="time" id="ctxTimeStart" class="modal-input" onchange="this.style.color='var(--text-pri)'; this.disabled=false; document.getElementById('ctxStartType').value='time'; document.getElementById('offsetRowStart').style.display='none'; document.getElementById('ctxOffsetStart').value='';">
<button type="button" class="btn" onclick="setSolarTime('Start', 'SUNRISE')">Sunrise</button>
<button type="button" class="btn" onclick="setSolarTime('Start', 'SUNSET')">Sunset</button>
</div>
<div class="input-row" id="offsetRowStart" style="display:none; margin-top:8px;">
    <span>Offset (min)</span>
    <input type="text" id="ctxOffsetStart" class="modal-input" placeholder="e.g. +30 or -15" oninput="updateSolarDisplay('Start')">
</div>
<input type="hidden" id="ctxStartType" value="time">
```

**B.2** The preview and token helpers:

```js
function formatSecondsToTime(secs) {
    if (secs === null || secs === undefined || secs === "") return "--:--";
    let s = Number(secs);
    if (isNaN(s)) return "--:--";
    let offsetSeconds = new Date().getTimezoneOffset() * 60;
    s = s - offsetSeconds;                 /* UTC -> local */
    s = ((s % 86400) + 86400) % 86400;     /* wrap */
    s = Math.floor(s / 60) * 60;           /* floor to minute */
    let h = Math.floor(s / 3600);
    let m = Math.floor((s % 3600) / 60);
    return (h < 10 ? "0" + h : h) + ":" + (m < 10 ? "0" + m : m);
}

function updateSolarDisplay(type) {
    const typeInput = document.getElementById('ctx' + type + 'Type');
    const timeInput = document.getElementById('ctxTime' + type);
    const offsetInput = document.getElementById('ctxOffset' + type);
    if (typeInput.value === 'time') return;
    const baseSecs = (typeInput.value === 'SUNRISE') ? solarStart : solarEnd;
    const rawVal = offsetInput ? offsetInput.value.trim() : "";
    let offsetMin = 0;
    if (rawVal !== "" && /^[+-]?\d+$/.test(rawVal)) {
        offsetMin = parseInt(rawVal, 10);
    }
    const adjustedSecs = Number(baseSecs) + (offsetMin * 60);
    timeInput.value = formatSecondsToTime(adjustedSecs);
    timeInput.style.color = 'var(--yellow)';
}

function setSolarTime(type, solarEvent) {
    const timeInput = document.getElementById('ctxTime' + type);
    const typeInput = document.getElementById('ctx' + type + 'Type');
    const offsetRow = document.getElementById('offsetRow' + type);
    const offsetInput = document.getElementById('ctxOffset' + type);
    if (typeInput.value === solarEvent) {
        timeInput.disabled = false;
        timeInput.value = '';
        timeInput.style.color = 'var(--text-pri)';
        typeInput.value = 'time';
        if (offsetInput) offsetInput.value = '';
        if (offsetRow) offsetRow.style.display = 'none';
    } else {
        const solarTimeSeconds = solarEvent === 'SUNRISE' ? solarStart : solarEnd;
        if (hasSolar && solarTimeSeconds !== null && solarTimeSeconds !== undefined &&
            solarTimeSeconds !== "" && !isNaN(parseFloat(solarTimeSeconds))) {
            typeInput.value = solarEvent;
            timeInput.disabled = true;
            if (offsetRow) offsetRow.style.display = 'flex';
            updateSolarDisplay(type);
        } else {
            showUIWarning("Solar Data Missing", "Sunrise/Sunset data is not available.");
        }
    }
}
```

**B.3** In `showAddContextForm`, the reset of each side and the edit-mode seeding:

```js
['Start', 'End'].forEach(type => {
    const el = document.getElementById('ctxTime' + type);
    el.value = ''; el.disabled = false; el.style.color = 'var(--text-pri)';
    document.getElementById('ctx' + type + 'Type').value = 'time';
    const offEl = document.getElementById('ctxOffset' + type);
    if (offEl) offEl.value = '';
    const offRow = document.getElementById('offsetRow' + type);
    if (offRow) offRow.style.display = 'none';
});
/* … edit mode, triggers.time_range present: */
[ { type: 'Start', val: sVal }, { type: 'End', val: eVal } ].forEach(item => {
    const type = item.type;
    const val = String(item.val || '').trim();
    /* timeInput / typeInput / offsetInput / offsetRow looked up as above */
    if (val.startsWith('SUNRISE') || val.startsWith('SUNSET')) {
        const solarEvent = val.startsWith('SUNRISE') ? 'SUNRISE' : 'SUNSET';
        typeInput.value = solarEvent;
        let offsetStr = '';
        if (val.includes('+')) {
            offsetStr = '+' + val.substring(val.indexOf('+') + 1).trim();
        } else if (val.includes('-')) {
            offsetStr = '-' + val.substring(val.indexOf('-') + 1).trim();
        }
        if (offsetInput) offsetInput.value = offsetStr;
        if (offsetRow) offsetRow.style.display = 'flex';
        timeInput.disabled = true;
        updateSolarDisplay(type);
    } else {
        typeInput.value = 'time';
        if (offsetInput) offsetInput.value = '';
        if (offsetRow) offsetRow.style.display = 'none';
        timeInput.value = val;
        timeInput.disabled = false;
        timeInput.style.color = 'var(--text-pri)';
    }
});
```

**B.4** In `commitContext`, building the saved token:

```js
function getCommittedTime(type) {
    const typeVal = document.getElementById('ctx' + type + 'Type').value;
    if (typeVal === 'time') {
        return document.getElementById('ctxTime' + type).value;
    }
    const rawOffset = document.getElementById('ctxOffset' + type).value.trim();
    if (rawOffset === "") {
        return typeVal;                                  /* blank means 0 */
    }
    if (!/^[+-]?\d+$/.test(rawOffset)) {
        showUIWarning("Invalid Offset",
            "Offset for " + type + " Time must be a signed whole number of minutes (e.g. +30, -15).");
        return null;
    }
    const offsetVal = parseInt(rawOffset, 10);
    if (offsetVal > 0) return typeVal + "+" + offsetVal;
    if (offsetVal < 0) return typeVal + offsetVal;       /* minus sign already present */
    return typeVal;
}
/* sVal = getCommittedTime('Start'); if (sVal === null) return;  (same for End) */
```
