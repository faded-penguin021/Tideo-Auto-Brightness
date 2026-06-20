# Tideo Auto Brightness — debug build

`tideo-auto-brightness-debug.apk` — debuggable APK built from branch `claude/wonderful-mendel-yzaga7` at segment **S13d** (UI finalization: real charts + About/User-Guide).

## Install

    adb install -r tideo-auto-brightness-debug.apk

## First run

- On launch you are taken through setup: grant **notifications** and **Modify system settings** (WRITE_SETTINGS) — required for the core loop. Optionally grant **WRITE_SECURE_SETTINGS** (adb / Shizuku / root) for super dimming.
- After setup you land on the **User Guide** (first-run only), then the **Menu** hub.

## What to look at for S13d

- **Reactivity / Circadian / Super Dimming** screens: swipe the chart pager above the settings — all charts now render real curves (no placeholders).
- **Tools**: Power-Draw chart shows an empty state until on-device calibration runs (deferred).
- **Menu → Info & Help → User Guide / About**: ported static screens.
- **Dashboard**: shows the active Profile + Context; the teal banner is now Menu-only.

_This `/dist/` folder is a temporary review artifact (committed so it reaches the branch). Delete it before merging — it should not land on `main`._
