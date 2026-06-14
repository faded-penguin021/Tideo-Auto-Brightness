# Gate 2 re-test build — Tideo Auto Brightness

**APK:** `tideo-auto-brightness-S12.6e-debug.apk` · branch `claude/dazzling-ride-1niyn6` · commit `e4aae26`
(S12.6a→e all landed) · minSdk 31 / target 35.

> This `dist/` folder (APK + this README) is a throwaway for the on-device Gate-2 re-test.
> **Delete `dist/` before merging the branch** once you've updated STATE.md and all findings are resolved.

---

## 1. Install (no compile needed on the phone)

1. Copy `tideo-auto-brightness-S12.6e-debug.apk` to the phone (USB, Drive, etc.).
2. Tap it in a file manager → allow "install unknown apps" for that app → Install.
   (It's a debug-signed APK; if a previous debug build is installed it will update in place.)

## 2. One-time permissions

- **Notifications** — allow when asked (or in the in-app Setup screen, step 1).
- **Modify system settings** (required for any brightness control) — Setup step 2 → toggle on for the app.
- **Elevated access** (optional — only needed for *super dimming* below the threshold). Setup step 3 offers
  three channels; pick one:
  - **ADB from a computer:** `adb shell pm grant com.tideo.autobrightness android.permission.WRITE_SECURE_SETTINGS`
    (the in-app "Copy command" button copies this).
  - **Shizuku** (if installed/running) → "Use Shizuku".
  - **Root** → "Try root".
- **Usage access** is now **optional by default** — only prompt-flagged once you add a per-app context rule.

After granting, open the app and turn the master switch on (Dashboard or the notification / QS tile).

---

## 3. What changed since the last re-test — focused checklist

The previous re-test produced findings **G2R-F1…F29**. S12.6a–d were verified earlier; **S12.6e** is new in
this build. Please re-verify the items below, then record results in `docs/rebuild/STATE.md` "Gate 2 RE-TEST".

### S12.6e — labels, help, context editor, onboarding (NEW in this build)
- **G2R-F19/F21 — labels & long-press help.** Open **Reactivity / Curve & Brightness / Misc / Super Dimming**.
  Each field now uses the Tasker label and shows an **ⓘ** button; tapping it reveals the **verbatim Tasker
  long-press help**. Spot-check a few against the Tasker app.
- **G2R-F19/F20 — "delta factor".** On **Reactivity** it is now **"Smoothing Δ"** and its ⓘ help reads
  *"Controls how much to smooth out sensor readings…"* (it controls sensor smoothing — the wiring was already
  correct; only the label/help were wrong).
- **G2R-F22 — Wi-Fi / location.** Menu → **Contexts** → Add rule:
  - **"Use current Wi-Fi"** should fill the SSID (needs Location permission + Location services ON; if not,
    you now get a *specific* message — "needs Location permission" / "turn on Location services" — not a
    blanket "Not connected").
  - **Location** section: **"Use current location"** fills lat/lon; radius field present.
- **G2R-F28 — time picker.** In the rule editor, tapping **From / To** opens the system **TimePicker** modal
  (the Sunrise/Sunset token buttons are still there).
- **G2R-F24 — usage access optional.** Setup screen shows usage access as **"(optional)"** until a per-app
  rule exists, then **"(needed for per-app rules)"**. "Done" is never blocked by it.
- **G2R-F25 — load toast.** When a context rule actually switches the active profile at runtime, a toast
  confirms it (e.g. *Context "Cinema" → profile "Video Streaming"*). Applying a profile from the Profiles
  screen also toasts.
- **G2R-F29 — Live Debug timings.** Menu → **Live Debug Info** → "Performance & Timings" now shows
  **Smoothing α (LuxAlpha), cycle time, reactivity cooldown (throttle), last animation (steps×wait),
  last update, last sample** (values in gold). Confirm they advance while the service runs.

### S12.6a–d — please re-confirm still good (verified earlier)
- **Menu is the home hub**; back from any settings screen returns to the **Menu** (not Dashboard).
- Renames: **"Super Dimming"** (was Animation & Dimming) and **"Circadian"** (was Dynamic Scale).
- Dashboard **"Last sensor sample"** advances (no longer "never").
- **Per-screen diagnostic cards** (Reactivity threshold/dead-zone; Circadian uncompressed vs true scale).
- **Apply / profile load takes effect immediately** (e.g. raise Min brightness → applies without a light
  change; min-brightness no longer "stuck at 10").
- **Manual override points** recorded; the **brightness curve overlays them** (fitted curve only at ≥9 points).
- No **false-positive** override-pauses on rapid light swings; **PWM-sensitive** holds the hardware floor.
- **Profiles:** Save current as… / Overwrite / Delete / **Restore factory profiles**; **block Apply** while a
  critical curve error stands (form2A/3A<0, form2C>zone1End); per-screen **Reset**; manual load shows a
  **Resume context automation** banner. **Legacy import** via linking `Download/AAB/configs` (SAF grant).

### Also re-walk the original Gate-2 set (G2-F1…F18)
Every field edits → shows `[committed]` bracket → **Apply** persists & re-runs the pipeline; sliders have the
right ranges; teal/gold AAB theme; debug-category selector is **global** (lives on Live Debug, survives a
profile load).

---

## 4. Recording results

Add findings under **"Gate 2 RE-TEST"** in `docs/rebuild/STATE.md` (new ones as `G2R-F30+`). If everything
passes, mark **Gate 2 signed off** there. Then delete this `dist/` folder before the branch is merged.
