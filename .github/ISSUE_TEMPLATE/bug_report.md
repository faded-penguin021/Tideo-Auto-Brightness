---
name: Bug report
about: Report an app-layer / Android bug in Tideo (crashes, OEM quirks, UI, packaging)
title: "[Bug] "
labels: [bug, needs-triage]
---

<!--
Tideo is the native-app build of Advanced Auto Brightness (AAB).
- App-layer / Android bugs (crashes, OEM secure-key quirks, battery-saver kills, UI/Compose glitches,
  packaging) belong HERE.
- Changes to the brightness MATH or a new FEATURE belong upstream at AAB:
  https://github.com/faded-penguin021/AdvancedAutoBrightness  (open an issue there first).
-->

### What happened

A clear description of the bug and what you expected instead.

### Steps to reproduce

1.
2.
3.

### Device & build

- **Device / OEM skin:** (e.g. Pixel 8 / stock, Xiaomi 13 / HyperOS)
- **Android version:** (e.g. Android 15)
- **Tideo version:** (Menu → About, e.g. 0.9.0)
- **Privilege tier:** BASIC (Modify system settings) / ELEVATED (Reduce Bright Colors)

### Logs / diagnostics

If relevant, enable **Live Debug** (Menu → Live Debug) and observe the readout toasts, or attach a logcat /
the curve-wizard "Copy full report" output.

### Notes

Anything else — does it reproduce with the service off, after a reboot, only on a specific OEM, etc.?
