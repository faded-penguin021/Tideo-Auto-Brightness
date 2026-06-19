# Validation parity audit (S12.9c #7)

Every Tasker-side validation signal (the focuschange/valueselected guard tasks + `_RedInvalidFormulae`
+ `_ValidateBrightnessParams`) vs. the rebuild's coverage in `SettingsValidator` / `AabSettings.validate()`
(clamp) / `AabSettingsContract`. Sources: `extraction/tasks/anonymous_handlers.md`,
`scenes/brightness_settings.md`, `scenes/superdimming_settings.md`, `tasks/task583`/`task707`/`task038`.

Two rebuild mechanisms:
- **clamp** = `AabSettings.validate()` hard-coerces the value on save (silent).
- **advisory/critical** = `SettingsValidator` returns a `FieldError` shown as red supportingText
  (CRITICAL ones also block Apply, G2R-F18).

## Covered (parity)

| Tasker guard | Rule | Rebuild coverage |
|---|---|---|
| task583 `_RedInvalidFormulae` (1) | `form2A < 0` | `SettingsValidator` form2A **CRITICAL** |
| task583 (2) | `form3A < 0` | `SettingsValidator` form3A **CRITICAL** |
| task583 (3) / task614/752 | `form2C > zone1End` | `SettingsValidator` form2C **CRITICAL** |
| task617 | `zone2End < zone1End` | `SettingsValidator` zone2End advisory + clamp |
| task707 `_ValidateBrightnessParams` | predicted brightness @1000 lux < 25 | `SettingsValidator` safetyBrightness advisory |
| task707 | MaxBright sanity / range | clamp `maxBright ∈ [minBright, 255]` + contract |
| (G2-F5) | dangerously low global scale | `SettingsValidator` scale advisory |
| task570/contract | min/max/offset/zone/anim ranges | clamp in `validate()` + contract `range …` strings |

## Gaps found, and what S12.9c did

| Tasker guard | Rule | Was | S12.9c action |
|---|---|---|---|
| **task505/circadian graph** | Spread (Circadian) signed `-100..100` | clamp wrongly `1..300` → negative (boost) path unreachable | **Fixed (#6):** clamp → `-100..100`, contract updated, `SettingsValidator` dimSpread advisory + boundary test. |
| **task607** | dimming strength clamped to 65 | silent clamp only | **Fixed (#7):** advisory "will be clamped to 65". |
| **task513** | dimming threshold < minBright (never engages) | not surfaced | **Fixed (#7):** advisory when dimming enabled. |
| **task665/689** | taper midpoint > maxBright (no headroom) | not surfaced | **Fixed (#7):** advisory when scaling enabled. |
| **task403/714/715** | min step wait > max step wait | silent clamp only | **Fixed (#7):** advisory. |

## Deferred to S14 (low value / clamp already protects; ≤ the threshold so not blocking)

These are pure reformat/clamp guards whose Tasker behaviour is already reproduced by `validate()`'s clamp
or by the bounded sliders, so only the *advisory message* is missing — cosmetic:

- task390/395/407/409/508/522/530/531/539/608/660 — single-field reformat/clamp on focus loss (range
  already enforced by clamp + slider bounds).
- task509/511/523 — dimming exponent / threshold / spread reformat (clamp covers the range).
- task403's throttle = `animSteps·maxWait + 10` derivation — already shown as a derived read-out, not a
  validated input.

Gap count surfaced as missing *messages*: 5 fixed here + ~14 cosmetic reformat guards deferred. The 5
fixed are the ones with real user-visible consequences (unreachable feature / never-engages / no
headroom / inverted range), per the ">10 gaps → fix top, defer rest (logged)" rule.

## No-glyph policy

All validator messages are plain English with **no `⚠️` glyph** (S12.9c non-goal). The two pre-existing
glyphs (scale, safetyBrightness) were stripped in this segment.
