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

The full Gate-2 re-re-test finding set (**F33–F73**) is addressed. Walk the app and confirm behaviour;
log anything wrong in `docs/rebuild/STATE.md` under the Gate findings. Highlights from this last session
(**S12.7i**, the bits most worth verifying on-device):

- **F70 — legacy import actually applies.** Profiles → *Link legacy configs folder* (grant
  `Download/AAB/configs`) → **Load** a real Tasker `.json` config. The imported curve/min-max/etc. should
  now **take effect immediately** on screen (previously it "loaded" by name but nothing changed).
- **F71 — override during the cooldown.** With *Detect overrides* on, manually change brightness shortly
  after the app last adjusted it (inside the throttle window). It should **pause/flag the override** rather
  than ignore it until the cooldown expires.
- **F72 — clear a time rule.** Contexts → edit a rule that has a From/To time → **Clear time** → Save. The
  rule should go back to "Always active" (no time constraint).
- **F73 — circadian scale tracks your real local sun.** With circadian scaling on, the morning ramp should
  follow your actual sunrise (not ~2 h late); `%AAB_ScaleDynamic` in **Live Debug** should be ≥ 1 during the
  day, not drop to ~0.85 mid-morning.

Also re-verify the earlier S12.7 areas if you want full coverage: override notification + QS-tile state,
context location lifecycle, no-Location Wi-Fi SSID, debug flashes / Live Debug scene, per-screen live
readouts, the curve chart (axis labels, legend, tap-to-delete points), and the profile load/save modals.

## After testing

1. Record results/findings in `docs/rebuild/STATE.md` (Gate 2 section).
2. When all findings are resolved, **remove `/dist/`** (APK + this README) before merging the branch —
   build artifacts should not land on `main`.
