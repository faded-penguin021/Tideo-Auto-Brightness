# Debug build — S12.8a (Gate-2 4th re-test salvage, Runtime)

`tideo-auto-brightness-S12.8a-debug.apk` — built from branch `claude/trusting-ramanujan-zbgkiz`.

This build lands the **S12.8a — Runtime** sub-segment of the Gate-2 4th-re-test salvage stage.
What to verify on-device (OnePlus 13 / Android 16):

- **F74** — Resume on the high-priority override notification now resumes the pipeline (even after a
  service restart).
- **F75** — the override alert no longer stacks beside the ongoing notification; it is cleared on
  resume / disable.
- **F76** — the ongoing service notification no longer shows a **Pause** action (Reset + Disable
  remain; Resume appears only while paused after a real override).
- **F77** — **panic gesture**: hold the phone **upside down and shake** while the display is on →
  S.O.S. vibration + brightness jumps to 255 + super dimming off + service turns Off.
- **F78** — throttle is derived from the **actual** animation `steps×wait`; after ~10 s of no
  brightness change it climbs to the `AnimSteps×MaxWait+10` ceiling (sensor stops polling).
- **F65** — with **PWM-sensitive** on, below the dimming threshold the hardware stays at the floor
  AND Android **Extra Dim** darkens below it (needs WRITE_SECURE_SETTINGS / ELEVATED).
- **F58** — Super Dimming screen shows the live dimming readout (relative + absolute level).
- **F86** — Live Debug / readouts clamp a displayed negative LuxAlpha to 0.
- **F88** — tap an AAB flash (global overlay) to dismiss it.

Transient artefact — remove before merging to main.
