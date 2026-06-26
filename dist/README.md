# dist/ — TEMPORARY test artifacts

**This directory is temporary and must be deleted before merge to `main`.**

It holds a debug APK for on-device testing of the `targetSdk 36` (Android 16) bump
on branch `claude/sdk-target-version-compat-wvma0e`. It exists only so the owner can
sideload and run the on-device acceptance passes (STATE.md "Active work").

- `tideo-auto-brightness-1.1.0-targetSdk36-debug.apk` — debug build, `versionCode 7`,
  `versionName 1.1.0`, `targetSdk 36` / `compileSdk 36`. Debug-signed (not the release
  key). Install with `adb install -r <apk>`.

Squash-merge keeps this off `main`; delete `dist/` before merging regardless.
