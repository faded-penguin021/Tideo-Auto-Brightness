# Tideo Auto Brightness — debug build for testing

**Temporary folder** — delete `/dist/` before merging the branch to `main`.

- APK: `tideo-auto-brightness-S12.9f-debug.apk`
- Built from branch `claude/youthful-ramanujan-li52xs` at the **S12.9f** commit
  (all of S12.9 a–f landed). Debug build; `versionCode = 1`, no public tag.
- Install: `adb install -r tideo-auto-brightness-S12.9f-debug.apk`
  (uninstall first if a different signature is already present).

This build is for the **HUMAN GATE 2 (6th) re-test** of the S12.9 stage. The
headline new behaviour to verify is the **Profiles & Contexts screen merge**
(S12.9f); the rest of S12.9 (b–e) is also live and worth a sanity pass.

## What to test — S12.9f (Profiles & Contexts merge)

The two separate "Profiles" and "Contexts" destinations are now **one screen**.

1. **One destination from the Menu.** The Menu shows a single **"Profiles &
   Contexts"** hero card (not two). Tapping it opens the merged screen. There is
   no standalone "Contexts" entry anywhere.
2. **Profiles surface unchanged** (top of the screen): save current settings
   as…, apply (with the "Load anyway" preview), overwrite, delete, restore
   factory profiles, reset to defaults, export, import (incl. legacy Tasker),
   and the legacy `Download/AAB/configs` folder link/load. All should behave
   exactly as before.
3. **Context rules section** (below profiles): lists existing rules in priority
   order, each showing its **target profile · priority** and a trigger summary.
   "Add rule" and per-rule "Edit"/"Delete" are present.
4. **Rule editing opens in a modal.** Add/Edit opens a full-screen editor.
   Confirm every trigger still works and persists on Save:
   - target-profile dropdown (lists your saved profiles)
   - Wi-Fi SSID + **Use current Wi-Fi**
   - time window via the **time picker**, the **Sunrise/Sunset** tokens (with
     resolved times), and **Clear time**
   - **day-of-week** picker
   - **location** lat/lon/radius + **Use current location**
   - **only while charging** + **battery %** from/to
   - **foreground apps** picker (+ the **usage-access** prompt if missing)
5. **Context lock + Resume preserved.** After a manual profile load, the
   "context automation paused" banner + **Resume** still appear on this screen,
   and the Menu hero card still reflects manual-override vs. active-context.
6. **Automatic switching still works.** A rule that matches (app/Wi-Fi/time/
   charging/location) should still switch profiles — the engine and rule storage
   were not changed, only the UI was merged.

## Quick regression sanity (rest of S12.9)

- **Circadian dimming (S12.9b, G2R-F90):** with super dimming enabled, Spread
  (Circadian) at 100 vs 0 should now actually change behaviour; super dimming
  should not over-engage in a bright/daylight-scaled context.
- **Profile-load errors (S12.9c):** importing a garbage/non-AAB file shows a
  visible error card instead of failing silently.
- **Dashboard staleness (S12.9d):** if the monitor stops responding while the
  service is on, the Dashboard shows the amber "live data may be stale" banner.
- **General stability (S12.9e):** start/stop the service, toggle the QS tile,
  reboot with auto-start on — no crashes (concurrency hardening, no behaviour
  change intended).

## Known limitations

- Visual polish of the merged screen is **deliberately minimal** — S13c does the
  Material 3 restyle. Report behaviour/parity issues, not styling.
- Debug build: extra debug toasts/flashes may appear depending on the Debug
  category selector (Live Debug Info screen).
