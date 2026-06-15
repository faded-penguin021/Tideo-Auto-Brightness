# Gate 2 (4th re-test) — debug build

This folder holds a ready-to-install **debug APK** so you can run the on-device Gate 2 check without
compiling on your phone. It is a throwaway artifact: **once you've recorded your Gate 2 findings in
`docs/rebuild/STATE.md` and everything is resolved, delete `/dist/` before merging the branch.**

- **File:** `tideo-auto-brightness-debug.apk`
- **Built from:** branch `claude/eager-newton-f6dcnc`, commit `1f0f362` (`[S12.7i]`) — all of S12.7 (a–i).
- **App id:** `com.tideo.autobrightness`  ·  **version** 1.0 (debug)  ·  **min Android 12 (API 31)**, target 35.

## Install (no compiler needed)

1. Copy `tideo-auto-brightness-debug.apk` to the phone (USB, Drive, etc.).
2. Open it with the Files app and tap **Install**. If Android blocks it, allow
   *"Install unknown apps"* for whichever app you opened it from.
3. **Sideloaded? Allow restricted settings.** Android 13+ blocks "Modify system settings" /
   Accessibility for sideloaded apps until you tap **⋮ → Allow restricted settings** on the app's
   *App info* page. The onboarding screen links you there; do this first or the grants below are greyed out.

## Permissions to grant (in the app's onboarding)

| Tier | Grant | Enables |
|---|---|---|
| Notifications | POST_NOTIFICATIONS | the ongoing service notification + override alerts |
| **BASIC** (required) | **Modify system settings** (WRITE_SETTINGS — user-grantable) | the whole core auto-brightness pipeline |
| Optional | Location (Fine/Coarse) | Wi-Fi SSID without ADB + location context rules |
| Optional | Usage access | per-app context rules |
| **ELEVATED** (optional) | WRITE_SECURE_SETTINGS via one-time `adb`/Shizuku/root grant | **Super Dimming** (below-floor dimming) |
| Optional | Accessibility service (opt-in) | system-wide debug "flash" toasts |

To grant ELEVATED from a PC: `adb shell pm grant com.tideo.autobrightness android.permission.WRITE_SECURE_SETTINGS`
(or use the in-app Shizuku/root flow). Without it the app still works; only Super Dimming is unavailable.

## What to check for Gate 2

This build closes the **entire Gate-2 re-re-test finding set (F33–F73)** across S12.7 a–i. The checklist
below is grouped by where you'll exercise each item; the **F-number** maps to the finding in
`docs/rebuild/STATE.md` so you can log results against it. Tick what passes, note anything that doesn't.

### Onboarding, permissions & navigation (S12.7d)
- **F33** Sideloaded install shows an *"Allow restricted settings"* card linking to App info before the grants.
- **F41** Setup has a **Location** permission step; Wi-Fi SSID can be read **without** Location (Shizuku
  `cmd wifi` → `dumpsys wifi` → Location callback as last resort).
- **F57** After granting permissions you land on the **Menu** hub (not a dead screen); **Back** navigates,
  it doesn't close the app.

### Manual-override engine (S12.7a)
- **F34** Normal auto-animations do **not** self-trigger an override; an opposing external brightness write does.
- **F64** Starting the service / turning the screen on / Resume / QS-on does **not** spuriously land in
  "paused by override".
- **F46** A manual **profile load** counts as an override (Menu shows it; **Resume** clears it). A context
  rule merely being active is **not** labelled an override.

### Runtime feedback — notification, QS tile, super dimming (S12.7b)
- **F35** A manual override posts a **high-priority, vibrating** notification **+ a toast**.
- **F40** The ongoing notification offers **Resume** alongside Pause and reflects paused/override state.
- **F63** The QS tile updates live (**Off → Starting → Active/Paused**) without reopening the panel.
- **F65** With **Super Dimming** + PWM-sensitive on (ELEVATED), "Extra Dim" actually **darkens below the
  threshold** (not just holds the floor).

### Context system (S12.7c)
- **F42** *"Use current location"* works right after granting (no false "not granted").
- **F43** The rule list is ordered by **priority, highest first**.
- **F44** A **legacy-imported** profile is selectable as a context-rule target without re-saving it.
- **F45** The location listener survives backgrounding (rules keep working), never reads `0,0`, and only
  re-fires after a **≥100 m** move (no toast spam).
- **F47** On an auto profile switch, the Context-Automation flash shows **trigger · context · profile ·
  rule (priority)**.
- **F67** The rule editor has a **day-of-week** picker; overnight windows wrap correctly past midnight.
- **F68** **SUNRISE/SUNSET** tokens show today's resolved time in gold (e.g. "Sunrise (06:42)").
- **F72** *(S12.7i)* **Clear time** removes a From/To rule so it returns to "Always active".

### Debug / toast infrastructure (S12.7e)
- **F48** The Dynamic-Scale debug flash fires **~2 min into a dawn/dusk transition**, not on every light change.
- **F49** On the unprivileged super-dimming fallback, an **overlay-preview** toast shows the overlay colour.
- **F50** Opt-in **Accessibility** service shows the flash messages **system-wide** (with a rationale screen);
  degrades to in-app toasts when off.
- **F51** Debug toasts **cancel, not stack**; turning a category off stops it immediately.
- **F52** The debug-category selector applies **instantly** (no back-out needed).

### Per-screen live readouts & labels (S12.7f)
- **F56** Live reactivity shows the threshold as a **percentage** (0.5 → "50%").
- **F58** **Curve & Brightness** and **Misc** show live readout blocks (smoothed lux / current brightness /
  throttle / smoothing α).
- **F59** The dynamic-threshold help substitutes the **live value** behind the ⓘ reveal.
- **F60** On **Misc**, "Scale" becomes a read-only **"(auto — dynamic)"** readout while circadian scaling is on.
- **F61** `form2A`/`form3A` are labelled **"Zone 2 / Zone 3 alignment"**.

### Charts & curve view (S12.7g)
- **F55** Both axes are labelled; the lux x-axis is **log (from 0.1)**; y-ticks are sane (no 191.25); drag/tap
  **scrubs** a readout across the chart.
- **F66** A **legend** distinguishes the live curve from the dashed gold reference line.
- **F62** The wizard's **suggested** curve is drawn on the Curve & Brightness chart (≥ 9 recorded points).
- **F69** The dashed reference line is **fixed** (committed snapshot) — it doesn't move as you edit fields.
- **F36** Override points are **tappable to delete** (shows the lux/brightness pair, confirms, writes back).

### Profiles, modals & circadian editor (S12.7h)
- **F38** Load / Save / Create-rule / Edit-rule modals show a **full settings list with gold
  changed-vs-default** highlighting; context **resume** feels smooth.
- **F39** The Circadian screen has a **Date + Latitude + Longitude** editor that defaults to **today +
  current location** when unset (preview-only).

### S12.7i fixes (verify these closely — newest)
- **F70** Profiles → *Link legacy configs folder* (`Download/AAB/configs`) → **Load** a real Tasker `.json`:
  the imported settings should **take effect immediately** on screen (not just "loaded by name").
- **F71** With *Detect overrides* on, manually change brightness shortly after the app last adjusted it
  (inside the throttle window): it should **pause/flag the override**, not ignore it until the cooldown ends.
- **F72** See *Context system* above.
- **F73** With circadian scaling on, the morning ramp follows your **real local sunrise** (not ~2 h late);
  in **Live Debug**, `%AAB_ScaleDynamic` is ≥ 1 during the day, not ~0.85 mid-morning.

## After testing

1. Record results/findings in `docs/rebuild/STATE.md` (Gate 2 section).
2. When all findings are resolved, **remove `/dist/`** (APK + this README) before merging the branch —
   build artifacts should not land on `main`.
