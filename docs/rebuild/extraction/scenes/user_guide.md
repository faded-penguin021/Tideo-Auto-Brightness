# Scene: AAB User Guide

- XML line range: **L8551–8868** (`<Scene sr="sceneAAB User Guide">`)
- Scene geom: 1440 x 2944 (portrait)
- Type: single full-screen **WebElement** rendering a long static HTML user manual + close button
- Target M3 screen: **About+Guide+Onboarding** (UserGuideScreen)

## Element count by type (top-level Scene children = 3)

| Type | Count |
|------|-------|
| WebElement | 1 |
| ButtonElement | 1 |
| PropertiesElement | 1 |
| **Total** | **3** |

(The WebElement carries one nested `background` RectElement — intrinsic styling, not counted as a top-level element.)

## Elements (in document order)

| # | name (sr) | type | bound variable | value range / options | handler task(s) | purpose |
|---|-----------|------|----------------|-----------------------|-----------------|---------|
| elements0 | (web, arg0="User Guide") | WebElement | — (static content) | arg1=2 (local HTML) | LinkClickFilter urlMatch="back" (stopEvent) | Full-screen static HTML user manual (9 sections, text below). |
| elements1 | Button1 | ButtonElement | — | icon `mw_navigation_close` (geom 1282,0,157,157, top-right) | clickTask=**717** | Top-right close (X) button → closes the User Guide scene. |
| props | props | PropertiesElement | — | title "AAB User Guide", bg #FF000000 | keyTask=**595** | Scene properties; back/key handler task 595. |

## Static TEXT content (port verbatim to UserGuideScreen)

Banner: **Advanced Auto Brightness** — User Guide

**Welcome to Advanced Auto Brightness**
> This project is a complete replacement for your phone's built-in auto brightness. Its goal is
> to provide a smoother, more intelligent, and completely customizable experience that adapts to
> light the way you want it to.

**1. Getting Started: Initial Setup**
- **Grant Permissions:** On first launch, AAB guides you through granting notifications and
  write-settings permissions (essential for the app to function).
- **Activate the Service:** In the main Brightness Settings screen, flip the master switch
  (top right). A persistent notification confirms AAB is active. This auto-disables Android's
  native auto-brightness.
- **Set Your Boundaries:** Hamburger menu (☰) → Misc. Adjust Min Brightness and Max Brightness
  sliders — the absolute darkest/brightest the screen will ever get.
- *Tip:* For OLED screens, set Min Brightness to a small value (5 or 10) instead of 0 to prevent
  "black crush" (loss of detail in dark areas).

**2. Navigating the Interface**
- All settings accessed via the hamburger menu (☰) top-left.
- Brightness Settings: shape of the core brightness curve.
- Reactivity Settings: behavior/sensitivity of the sensor.
- Misc Settings: rules and feel (master limits, animations).
- Circadian: brightness scaling by time of day.
- Superdimming: ultra-low brightness and PWM flicker-reduction.
- Profiles: custom configurations and pre-tuned presets.
- *Your Secret Weapon:* Long-press any setting's label for a detailed tooltip.

**3. Tuning Your Brightness Curve (Brightness Settings)** — three-part formula:
- **Zone 1 (Darkness to Dim Light):** Zone 1 Scaling = main low-light control (higher = brighter
  faster in the dark); Zone 1 End = lux level where "dark" ends and "dim light" begins.
- **Zone 2 (Dim to Medium Light):** Zone 2 Scaling & Offset shape the curve for common indoor
  light; use the graph to see the transition.
- **Zone 3 (Bright Light):** handled automatically, smoothly approaching maximum brightness.
- **Automatic Curve Fitting:** With Override Detection on, AAB saves your manual slider points.
  Gather data in ≥9 lighting conditions → Brightness Graph → "Suggest values" → review on graph →
  "Apply".
- *Tip:* The engine reports R², nRMSE, and bias; detailed analysis is in the `%AAB_Test` variable.

**4. Eliminating Flicker (Reactivity Settings)**
- A smart dead zone removes fluctuations from sensor noise; the screen reacts only on significant
  light changes.
- Thresholds: Dark, Dim, Bright Threshold control dead-zone size (higher = more stable).
- Curve: Curve Mid and Slope control the smooth sensitivity transition between dim and bright.

**5. Profile Management (Configs & Presets)**
- Profiles menu → save current config as a named file; swap between strategies.
- Dashboard Tracking: compares active settings to factory defaults; tuned values appear in yellow.
- System Presets: Battery Saver, Outdoors (modifiable). Default profile is modifiable but not
  deletable from the UI.
- Automatic Backup: a timestamped backup is created after updating to V3.3.

**6. Automatic Profile Switching** (Context Engine)
- Rules pair triggers with a profile; matching triggers auto-load the profile. No match → falls
  back to last manually-loaded profile.
- **Triggers:** App (foreground app); Time & Day (window + optional days, supports SUNRISE/SUNSET);
  Battery (% range + optional charging filter); Location (geofence radius around saved coord);
  Wi-Fi (specific network).
- **Conflict Resolution:** Priority (1–100, higher wins) decides first; ties broken by more
  triggers (more specific).
- *Tip:* Low-priority 7-day outdoor rule + higher-priority geofence/Wi-Fi indoor rules.
- **Pausing & Resuming:** Manually loading a profile pauses the context engine; resume from the
  Profile screen or by cycling the screen off/on.
- *Battery Note:* Location rules need GPS + Wi-Fi scanning; prefer Wi-Fi rules over geofences for
  lower power.

**7. Advanced Features**
- **Dynamic Scaling (Circadian Engine):** adjusts overall curve by time of day relative to
  sunrise/sunset (brighter by day, dimmer at night).
- **Super Dimming & PWM Sensitive Mode:** Super Dimming goes darker than Android's minimum via
  elevated access or a screen-filter overlay fallback. PWM Sensitive Mode fixes hardware brightness
  at a flicker-free floor (the PWM Threshold) and uses a software overlay for lower perceived light.
- *IMPORTANT WARNING:* Set the PWM Threshold to the **lowest** value that eliminates flicker. Too
  high keeps pixels at high power while dimmed by overlay → accelerated screen aging / burn-in.
- **Manual Override:** adjusting the slider pauses AAB and posts a notification; tap "Resume".
- **Emergency Recovery (Panic Button):** shake the device vertically while the phone is upside
  down → reset, stops automation, forces max brightness, S.O.S. vibration confirms.
- **Quick Settings Tile:** native QS tile to Pause/Resume or toggle the service (Tasker version
  requires assigning Tasker QS Tile 3 to AAB).

**8. Visualize Before You Apply: Use the Graphs!**
- Brightness Graph: Teal Line = your curve, blue dots = manual override points, Yellow Line =
  reference.
- Reactivity Graph: stability curve and light change required to trigger an update.
- Other Graphs: Circadian and Superdimming sections visualize behavior over time / brightness.
- **Power Draw Calibration (Debug Menu):** experimental tool — in Airplane mode with background
  apps closed, steps through all brightness levels, measures actual current (mA), generates a
  Power Curve. May take multiple runs.

**9. Saving Your Work**
- **Apply:** makes changes active immediately, *not* written to disk; lost on profile load / app
  restart.
- **Save (Profile screen):** writes all current settings to the selected profile's JSON file —
  the only way to make changes permanent.
- **Reset:** reverts the current tab's settings to defaults.
- **Exit:** closes the panel; applied-but-unsaved changes stay active until a profile loads.
- *Tip:* After editing multiple tabs, go to the Profile screen and save to your active profile;
  if using the context engine, overwrite the relevant profile or changes are replaced when a rule
  fires.

> Enjoy your new intelligent auto brightness!

## Disposition

| Element | Disposition |
|---------|-------------|
| elements0 (User Guide web manual) | user_guide — kept-as About+Guide+Onboarding |
| elements1 (close button) | user_guide — dropped(M3 nav back replaces in-scene close button) |
| props (scene properties / back task 595) | user_guide — dropped(handled by Compose navigation) |
