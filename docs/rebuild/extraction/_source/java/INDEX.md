# Embedded-Java extracts (reference transcripts — NOT buildable)

These 40 `*.java.txt` files are the verbatim embedded-Java blocks (Tasker action **code 474**) pulled
from the Tasker export `Advanced_Auto_Brightness_V3.3.prj_9.xml`, `html.unescape`-decoded from their
XML-entity-encoded `arg0` source. They are **reference transcripts only**:

- They are **not compilable Java**. Tasker substitutes `%AAB_*` variable *values* textually into the
  source before compiling, and the surrounding Tasker scaffolding (scene/var context) is absent. The
  reference implementation in `domain/src/test` parameterizes those `%var` sigils — see `TaskerReference.kt`.
- The `.txt` suffix (added in S12.9a) keeps them out of any Java/Kotlin toolchain and signals
  "documentation, not source". They previously lived at `docs/rebuild/extraction/java/*.java`.
- Naming: `task<id>_<block#>_<slug>.java.txt`. A few tasks have two blocks (e.g. `task90_1`/`task90_2`,
  `task618_1`/`task618_2`, `task630/631/663`).

**Cross-validation rule (CLAUDE.md):** `task663` (`_GenerateGraph`) holds a plot-side copy of the
3-zone formula — cross-validate against it, never port from it. `task661` ("Map Lux to Brightness")
contains NO Java; its curve math is in code-547 maths expressions, not here.

| Cluster | Files |
|---|---|
| Sensor / smoothing / mapping | `task554` (process-sensor-event), `task535` (lux-smoothing), `task544` (evaluate-light-change), `task546` (set-thresholds) |
| Brightness formulae / animation | `task543` (calculate-animation), `task696` (smooth-brightness-transition), `task698` (smooth-DC-like-transition), `task618_1/2` (set-initial-brightness) |
| Circadian / dynamic scale | `task90_1/2` (dynamic-scale-v13), `task548` (dynamic-range-compressed-scale) |
| Curve wizard | `task38` (suggest-curve-parameters / AAB Curve Fitting Engine V43.8), `task655` (set-suggested-variables) |
| Contexts | `task43` (evaluate-contexts), `task623` (context-manager), `task625` (app-picker), `task626` (context-resume), `task630_1/2` (loc'n listener), `task631_1/2` (net+loc), `task633` (get-wifi-for-context), `task105` (get-wifi-no-location) |
| Profiles | `task592` (create-default-profiles), `task637` (profile-manager), `task636` (delete-override-point) |
| Privilege / permissions | `task378` (privilege-detection), `task563` (ask-permissions), `task643` (learn-write-secure) |
| Calibration | `task524` (calibrate-power-draw), `task620` (adaptive-brightness-scene-size) |
| Graph generators (plot-side; cross-validate only) | `task549` (circadian), `task556` (dimming-curve), `task557` (alpha), `task657` (compression), `task663_1/2` (3-zone), `task703` (reactivity), `task705` (circadian-dimming) |
| Owner revisions (not in the XML) | `task43_1_evaluatecontexts-v2.rev-2026-10-01.hunks.txt` — the owner's 2026-10-01 paste of task43 act12's changed regions (solar offsets, inclusive end minute, exit wake at end + 60 s); the rest equals `task43_1` apart from a cosmetic list. It supersedes `task43_1` L314-357 and is cited by line number (DD-035); `task637_profilemanager-webview.rev-2026-10-01.hunks.txt` — the same date's paste of the Profile Manager webview's changed regions (the Offset field, preview and token helpers that `RuleEditor` ports, DD-035, DD-036) |
