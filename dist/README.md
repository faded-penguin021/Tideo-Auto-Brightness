# Gate 2 re-test — Tideo Auto Brightness (S12.5 UI salvage)

This folder holds a ready-to-install **debug APK** so you can run the Gate 2 walkthrough on your
phone without compiling. It is **throwaway**: once you've recorded findings in `STATE.md` and the
remaining items are resolved, delete `dist/` before merging the branch (it only lives in branch
history).

- **APK:** `tideo-auto-brightness-gate2-retest-debug.apk`
- **App ID:** `com.tideo.autobrightness`  ·  **versionName** 1.0  ·  **minSdk** 31 (Android 12+)
- **Build:** debug, unsigned-for-store (debug-signed). Branch `claude/upbeat-mayer-dpz1v2`, segment S12.5c.

## Install

1. Copy the APK to the phone (USB, Drive, etc.) and tap it, **or** `adb install -r dist/tideo-auto-brightness-gate2-retest-debug.apk`.
2. Allow "install from this source" / dismiss the Play Protect warning (expected for a debug build).
3. If a previous Tideo build is installed and the install is refused, uninstall it first
   (`adb uninstall com.tideo.autobrightness`).

## Permissions to grant (same as Gate 1)

- **Notifications** — prompted on first launch (Android 13+).
- **Modify system settings** (WRITE_SETTINGS) — onboarding link, or Settings → Apps → Special access.
- **Extra dim / WRITE_SECURE_SETTINGS** (only for the super-dimming checks) — via ADB or Shizuku:
  `adb shell pm grant com.tideo.autobrightness android.permission.WRITE_SECURE_SETTINGS`
- **Usage access** — the app now prompts you when you add an app-based context rule.

## What S12.5 changed — focus your re-test here

Gate 2 (first attempt) judged the UI "miles off" Tasker. S12.5a/b/c reskinned + rewired the UI. Please
re-verify the whole look/feel **and** these specific fixes (Gate-2 finding IDs in brackets):

### Look & interaction model (S12.5a / S12.5b)
- [ ] **Teal + gold identity**, project name in the top header, **hamburger nav-drawer** (AAB Menu),
  **hero cards** for Profiles + Contexts on the Dashboard. *(F18)*
- [ ] **Preview → Apply** on every parameter screen: edits update the graph live but only **Apply**
  commits; each field shows the committed value in `[brackets]` while a draft differs; Back/Discard
  reverts (dirty-back is confirmed). *(F1/F7)*
- [ ] **Bounded sliders** with the exact Tasker ranges: Min 0–75, Max 150–255, AnimSteps 0–100,
  MinWait 1–99, MaxWait 2–100, Taper-midpoint 130–240. *(F3/F13)*
- [ ] **Misc screen** holds min/max/offset/scale + animation + notifications + the debug selector;
  Curve & Brightness = curve coefficients only; Animation & Dimming = super-dimming + PWM only
  (mutually exclusive); dim-spread gated on circadian. *(F2/F10/F11)*
- [ ] Applying a profile / changing settings **re-runs the pipeline immediately** (brightness reacts
  even with no new light reading, e.g. in a dark room). *(F16)*
- [ ] Validation: min-bright moves the curve floor; scale < 0.5 warns; zone2End < zone1End warns
  instead of NaN. *(F4/F5/F6)*

### Behaviour fixes (S12.5c — the new ones)
- [ ] **Override detection survives a profile load** *(F8)*: turn on **Reactivity → Detect manual
  overrides**, load a built-in profile (Profiles screen), then grab the system brightness slider mid-run
  → it should still pause to a manual override. Previously a profile load silently turned it off.
- [ ] **Toasts** *(F12)*: a short toast confirms Apply, profile apply/reset, import/export, context
  rule save/delete, and wizard apply/copy.
- [ ] **Context rule editor** *(F14)*: the app picker is **populated with icons**; "Use current Wi-Fi"
  fills the SSID; **Sunrise/Sunset** buttons fill the From/To fields; adding an app trigger prompts for
  **usage access**.
- [ ] **Debug toasts** *(F15)*: on Misc, pick a `Debug:` category (e.g. "Light Eval Thresholds",
  "Super Dimming Info", "Context Automation"). With the service running you should see runtime toasts
  for that category as the pipeline cycles. Also in **Tools → wizard → Copy report** copies the
  diagnostics to the clipboard.
- [ ] **Super dimming** *(F9)*: with WRITE_SECURE_SETTINGS granted, enable Animation & Dimming →
  Super dimming, set the debug category to **"Super Dimming Info"**, and dim the room below the
  threshold. The toast should say `ON level N`. **If it says "ON level N" but the screen does not
  visibly dim**, that is an OEM "Extra dim" secure-key difference on your device, not a logic bug —
  please note the **phone model / Android skin** in the findings.
- [ ] **QS tile** *(F17)*: add the Auto Brightness Quick Settings tile; its **subtitle** should read
  Off / Active / Paused / Starting and update when you pause via the notification.

## Recording findings

Add results under **Gate findings → Gate 2** in `docs/rebuild/STATE.md` (pass/fail per finding, plus
device model for any OEM-specific dimming note). When every finding is resolved/accepted, **delete the
`dist/` folder** and merge the branch.
