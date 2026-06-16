# Gate 2 (5th re-test) — build brief

> **APK refreshed 2026-06-16 16:16** with the follow-up fixes from your first pass. Re-check these:
> - **F70 (Form1A decimal):** load the legacy JSON with `Form1A 5.833` again — the Curve & Brightness
>   field should now read **5.833**, not 6. (Root cause was the schema storing form1A as an integer; it's
>   now a continuous Double, so the wizard's suggested form1A also lands exactly.)
> - **Dashboard brightness:** the "X → Y" readout is now a single **`N / 255`** value (the two numbers
>   were always identical because the pipeline only reports state after the animation finishes).
> - **Charts:** Circadian's first graph is now titled **"Circadian"** (not "Experiment"); **Super Dimming
>   now swipes between "Dimming curve" and "Circadian Dimming"**, and the **Spread (circadian)** control is
>   grouped under "Circadian Dimming". The **suggested curve + override dots are now Tasker blue**
>   (`rgb 54,162,235`), not gold.
> - **F59:** no change needed — F85 already removed the field that printed the literal token; the value
>   shows as a % in the live reactivity card.
> - **F39/F73:** your observed **1.131** is *correct for the frame used* but ~1 h off the intuitive
>   "21 Dec @ 17:34 local" (it applies today's summer +2 offset to a winter date). That's the **F73
>   fixed-date offset residual** you're checking this evening — a circadian (S12.8d) item, not UI.
>
> ---


**APK:** `dist/app-debug.apk` (com.tideo.autobrightness, debug, versionName 1.0)
**Build branch:** `claude/modest-tesla-mpe7es` (S12.8b) — contains **all of S12.8 a + b + c + d**.
**Device target:** OnePlus 13 / Android 16 (your last test rig).
**Note:** `/dist/` is transient — delete it before merging the PR.

This build closes the whole S12.8 salvage stage. You already tested **S12.8a** at the time it was
pushed, so the focus below is **b, c, d** (a's items are listed last as a regression spot-check).

## Install / setup
1. Sideload `app-debug.apk` (replace the previous build; a clean reinstall is cleaner for the schema
   migration in 8c — see F85 below).
2. Run through onboarding: notifications → Modify system settings → Location → (optional) elevated
   (adb/Shizuku/root) `pm grant WRITE_SECURE_SETTINGS` for super dimming + usage access for per-app rules.
3. Sideloaded installs: if a grant is blocked, use the in-app "Allow restricted settings" hint
   (App info → ⋮ → Allow restricted settings).

---

## S12.8b — UI (this session)

- **F79 Dashboard redesign** — Dashboard should feel purposeful: a status headline, the master
  on/off switch (there is **no Pause button** anymore), and live cards (ambient light raw/smoothed +
  "last sample Xs ago", brightness current→target, circadian scale ×, super-dim %, active context).
  *Check:* change the screen brightness by hand → an **override card with "Resume auto brightness"**
  appears; Resume hands control back. With no override, there is no Resume button.
- **F81 graph above settings + swipe** — open **Reactivity**, **Super Dimming**, **Circadian**: the
  chart sits **above** the settings (Super Dimming's used to be at the bottom). Where a screen has two
  relevant graphs (Reactivity = curve/alpha, Circadian = experiment/taper) you **swipe** the chart
  area to switch graphs (dots show the page). *Charts themselves are still S13 placeholders* — you are
  checking placement + swipe, not the rendered curve.
- **F82 grouping** — under each chart, the settings that feed it are in an outlined group captioned
  "Affects the … graph".
- **F68 sunset label** — Contexts → add/edit rule → Time window: the **"Sunset (HH:MM)"** token reads
  on one line (was wrapping one letter per line).
- **F87 taller app list** — Contexts rule editor → "Foreground apps" list is taller (still scrolls).
- **F89 permissions** — informational: background location is now declared; DUMP is intentionally not
  requested. Nothing to tap; just confirm nothing regressed in Location/usage-access prompts.

## S12.8c — settings & profiles (untested by you)

- **F85 (critical)** — Reactivity no longer has an editable **"Dynamic threshold"** field (it was a
  computed value, not an input). It now shows read-only as a **percentage** in the live reactivity
  card. *A clean reinstall avoids any stale-schema confusion (schema bumped + migrates).*
- **F59** — the dynamic-threshold help text substitutes the live value, not the literal
  "%AAB_ThreshDynamic".
- **F84** — the settings-diff modals (Load/Save/View current settings on **Profiles**) use friendly
  labels (e.g. "Zone 1 scaling", not "form1A") and hide irrelevant keys.
- **F70 legacy load** — import a real exported Tasker/AAB config (Profiles → import / link folder):
  values should = task570 defaults **then** the file's diffs. *Check Form1A actually sticks* and misc
  fields are not inherited from the previously loaded profile.
- **F62 wizard** — Tools → curve wizard must **not** fit on fewer than 9 *real* recorded override
  points (it used to inflate with synthetic priors); when it does fit, the suggested curve draws on
  the Curve & Brightness chart.

## S12.8d — circadian time & location (untested by you)

- **F73** — the dynamic-scale ramp should track the **real local sun** (no ~1 h DST/offset error vs
  the context-rule sunrise/sunset). *Check:* on the Dashboard / Live Debug, the circadian scale near
  local sunset should move in step with the context sunrise/sunset times, not ~1 h early.
- **F39 fixed date/location** — Circadian → "Date & location": set a fixed date (e.g. **21 Dec**)
  and/or a location; "Set fixed" must actually **change the circadian scaling** (date-only, loc-only,
  or both all work). "Use live data" reverts to today + current location.
- **F83 location acquisition** — with no Android fix available, location falls back to ip-api.com
  (needs network); it is skipped entirely when a fixed lat/lon is pinned.

---

## S12.8a regression spot-check (you tested this already)

Quick confirm these still hold: override notification **Resume** works and doesn't stack; the ongoing
notification has **no Pause** action; throttle climbs after ~10 s idle; the **panic gesture**
(sustained upside-down + shake, screen on) fires SOS vibration + 255 + service off; PWM-sensitive
mode dims below the floor via Extra Dim; Super Dimming screen shows the live dimming readout.

## Reporting
Note pass/fail per finding ID (F79/F81/F82/F68/F87/F89 · F85/F59/F84/F70/F62 · F73/F39/F83), plus any
new observations as G2R-F90+. Domain math / golden vectors were untouched this stage.
