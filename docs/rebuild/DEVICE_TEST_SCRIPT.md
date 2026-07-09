# DEVICE_TEST_SCRIPT — Gate 3 on-device acceptance

Run this end-to-end on a real device (no emulator — the SoC has no KVM here, and the light/proximity/
battery sensors, OEM brightness range, Shizuku binder, and doze are only exercisable on hardware). Tick
each step's **Expected**; log any miss in `STATE.md` → "Gate findings". Build + install the debug APK
with `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (or grab a published
build from Releases).

Legend: **[BASIC]** needs only WRITE_SETTINGS · **[ELEVATED]** needs WRITE_SECURE_SETTINGS · **[opt]**
optional.

## 0. Install & onboarding (task563)

1. Install the APK; launch. **Expected:** onboarding starts (tier == NONE) → notifications prompt →
   "Modify system settings" → optional Location → optional Elevated step → optional usage-access.
2. Grant POST_NOTIFICATIONS and WRITE_SETTINGS. Return to the app. **Expected:** tier badge shows
   **BASIC**; first-run lands on the User Guide, Back → Menu.
3. Disable the system's stock Adaptive Brightness (Settings → Display). **Expected:** no fighting
   between Tideo and the OS.

## 1. Core loop — sensor → brightness (prof760/task554/544/535/661/543/696)

4. From the Dashboard, flip the **master switch** on. **Expected:** a persistent foreground
   notification appears (live lux/target); the QS-tile/widget (if added) shows Active.
5. Cover the light sensor with a finger. **Expected:** brightness animates **down smoothly** (no jump);
   the Dashboard's big number **rolls** to the new value and the teal bar depletes.
6. Shine a light on the sensor. **Expected:** brightness animates **up smoothly**.
7. Hold the light steady ~15 s. **Expected:** no flicker/oscillation (reactivity dead band); Live Debug
   shows the throttle climbing to its ceiling in stable light.

## 2. Manual override detect / resume (prof755/task567)

8. With Override Detection on (Reactivity screen), drag the **system** brightness slider mid-run.
   **Expected:** Tideo **pauses**, posts a vibrating high-priority "manual override" notification +
   teal flash; the Dashboard shows the Resume card.
9. Tap **Resume** (notification or Dashboard). **Expected:** auto control resumes from the current lux.
10. Rapidly swing the light up/down during an animation. **Expected:** NO false "override" pause
    (the task567 settle re-read absorbs the pipeline's own multi-frame writes).

## 3. Screen off/on — hibernate & reinit (prof753/585, prof761/618)

11. Turn the screen off, wait ~10 s, turn it on. **Expected:** sensing resumes; an initial brightness is
    set for the ambient level; context automation resumes (manual lock cleared on wake).
12. Reboot the device. **Expected:** the service self-starts (foreground notification returns) if it was
    enabled (specialUse FGS is boot-eligible).

## 4. Proximity damp (prof759/task545) — NEW S14

13. With the service running in changing light, cover the **top** of the phone (proximity "near", e.g.
    hold it to your ear). **Expected:** brightness reactivity is **damped** (changes ~10× slower) but the
    loop does **not** pause; uncovering restores normal reactivity. (Live Debug LuxAlpha drops while near.)

## 5. Panic reset (prof769/task528) — sensitivity tuned in S14

14. Hold the phone **upside down** (charging port up) and **shake** vertically. **Expected:** an **S.O.S.
    vibration**, brightness forced to **maximum**, the service stops (full reset).
15. **Grab the phone out of a pocket and turn the screen on normally** (do not deliberately invert+shake).
    **Expected:** panic does **NOT** fire — the 3 s post-wake grace + the stricter inversion threshold
    suppress the grab-to-wake false trigger.

## 6. Super dimming [ELEVATED] (task646/650/645/700/698)

16. Grant elevated access:
    `adb shell pm grant com.tideo.autobrightness android.permission.WRITE_SECURE_SETTINGS`
    (or Shizuku one-tap / root from onboarding). **Expected:** tier badge → **ELEVATED** on the next
    screen-on / app resume (S14: the tier is cached and refreshed on resume, not re-checked per cycle).
17. Enable **Super Dimming** (Super Dimming screen). In a dark room below the dimming threshold.
    **Expected:** the screen darkens **below** the normal minimum (Extra Dim engages); raising the light
    above the threshold disengages it cleanly. Live readout shows %AAB_DimmingCurrent/DS.
18. **[opt]** Toggle **PWM-sensitive** mode instead. **Expected:** hardware brightness holds at the PWM
    floor while the secure layer dims below it.
19. **Circadian dimming:** set Spread (Circadian) to 100 with circadian scaling on, in daylight hours.
    **Expected:** super dimming is **suppressed** during the circadian daytime boost (G2R-F90).

## 7. Circadian scaling (task90)

20. On the Circadian screen, enable dynamic scaling; check the chart's **"Now"** line and the live curve.
    **Expected:** the scale multiplier tracks the real local sunrise/sunset (not a fixed UTC window).
21. Set a **fixed date/location** (Experiment element). **Expected:** the curve + the live scaling shift
    to that day/place; "Use live data" reverts.

## 8. Contexts (task43 + prof762–768)

22. Add a **per-app** rule (grant usage access when prompted) targeting a saved profile; switch to that
    app. **Expected:** the profile loads (a teal context flash); the Dashboard shows the active context.
23. Add a **charging** rule; plug/unplug. **Expected:** the rule applies on the charging change.
    - **Prompt switch on plug-in (D-132).** With a higher-priority charging rule and a lower-priority
      battery rule both matching (e.g. "Charging" P81 on-power vs "Low battery" P80 ≤30%), at low battery
      plug the charger in (screen can be off). **Expected:** it switches to the charging rule **immediately**,
      not after the next battery % tick (the plug event bypasses the 30 s battery cooldown).
24. **[opt]** Add a **Wi-Fi/SSID** or **location** rule. **Expected:** applies on connect / on entering
    the radius (location rules only run when configured — battery gate).
    - **No-Location SSID read (D-130).** In the rule editor tap **Use current SSID** with Location
      services **off** and no Shizuku/root grant. **Expected:** the SSID-help dialog appears (explains
      the Location requirement + the Shizuku/root and ADB-DUMP alternatives; **Copy ADB command** copies
      `adb shell pm grant <pkg> android.permission.DUMP`). Then grant DUMP (`adb shell pm grant
      com.tideo.autobrightness[.debug] android.permission.DUMP`), keep Location off, tap **Use current
      SSID** again. **Expected:** the field fills with the connected network name (resolved via in-process
      `dumpsys wifi`). With Shizuku or root instead, the same read succeeds via `cmd wifi status`.
25. Manually load a profile (Profiles). **Expected:** context automation **pauses** (Resume banner);
    screen off→on or Resume re-enables it.

## 9. Charts, wizard, calibration, profiles

26. Collect ≥ 9 manual overrides (step 8 repeatedly across lighting), then **Tools → Run wizard**.
    **Expected:** a fitted curve + a verbose diagnostics report; **Apply** updates the curve; the Curve &
    Brightness chart shows the recorded points + suggested line + the live "Now" marker.
27. **Tools → Calibrate power draw** (Airplane Mode on, unplugged). **Expected:** the prep dialog → a
    brightness sweep (~1–2 min) → the **Power Draw chart** fills with the measured curve; "Recalibrate"
    re-runs it. Unplugged/charging and no-current-sensor are handled with a message.
28. Save / overwrite / restore-factory profiles; **export** then **import** (JSON and a legacy Tasker
    config). **Expected:** round-trips faithfully; invalid files show an error card.
29. Enter an out-of-range value on a settings screen and **Apply**. **Expected:** it is **clamped** to a
    safe value on commit (S14 — no unsafe value reaches the engine); critical form errors block Apply.

## 10. Surfaces & soak

30. Add the **QS tile** and the **home-screen widget** (Dashboard quick actions). **Expected:** both
    reflect Off/Active/Paused and toggle the service; the widget repaints on state changes.
31. **24 h soak:** leave the service running a full day across doze/charging/locations. **Expected:**
    survives doze (service not killed — exempt from battery optimization if needed, see dontkillmyapp),
    acceptable battery drain, **no ANRs/crashes**, brightness stays sensible.

## 11. Privileged Display toggles [ELEVATED] (D-149–D-152) — NEW 1.7.0

The toggles are `AabSettings` **profile fields** applied on profile change by
`DisplayTogglesCoordinator` (super-dimming model, idempotent only-on-change); with the service OFF,
Apply writes the device directly (`applyNow`). Debug builds need their own grant
(`… com.tideo.autobrightness.debug …`, D-106).

32. **Each toggle writes + reads back.** Service **OFF** (exercises the direct-write path), on
    **Menu → Privileged → Privileged Display** change one field at a time and **Apply**; confirm the
    device reacts AND the system Settings UI agrees (a wrong key would be silently *created*, not
    rejected — visible agreement is the test):
    - **Night Light** on, temperature slider near 2596 K. **Expected:** screen visibly warms;
      Settings → Display → Night Light shows ON with matching intensity. "Use device temperature"
      (unset) leaves the system's own preference untouched. ⚠️ **Known variance (2026-07-05,
      owner's OnePlus):** OxygenOS ignores `night_display_color_temperature` — the tint is the
      same regardless of the Kelvin value (the switch itself works). The slider and step 38's
      circadian tracking are then visually inert on that device (D-048: documented, not branched;
      the write still lands in the settings table — verify over adb if desired).
    - **Color correction:** Grayscale, then Protanomaly/Deuteranomaly/Tritanomaly. **Expected:** the
      filter matches; Settings → Accessibility → Color correction shows the same mode.
    - **Color inversion** on/off. **Expected:** inverts; the Accessibility toggle agrees.
    - **Always-on display** on/off. **Expected:** AOD appears/disappears on the lock screen.
    - **Stay awake while charging** on, short screen timeout, charger in. **Expected:** the screen
      never sleeps while plugged (AC/USB/wireless); off + unplugged, normal timeout returns.
    - **Force SDR** (visible only on Android 14+) on → **read back over adb:**
      `adb shell settings get global user_disabled_hdr_formats` → `1,2,3,4` and
      `adb shell settings get global are_user_disabled_hdr_formats_allowed` → `0`; an HDR video
      plays without the HDR brightness boost. Off → allowed returns `1` and HDR plays again.
33. **Profile carried by a context rule — engage AND baseline restore.** Keep the baseline's display
    fields all off/default. Set grayscale (+ Night Light) on Privileged Display, **save as a
    profile**, then restore your baseline values. Add a Contexts rule loading that profile (a time
    window a few minutes out, or a per-app rule); service ON. **Expected:** rule matches → grayscale
    + Night Light engage (context flash, Dashboard shows the context); rule ends → the **baseline
    profile's values** return (restore-to-baseline, not a remembered device pre-state — D-151).
34. **Apply with the service OFF hits the device (applyNow).** Master switch OFF; change any display
    field; Apply. **Expected:** the device changes immediately. Then start the service.
    **Expected:** nothing reverts — the coordinator's seed *adopts* the baseline without writing.
35. **Manual/system changes between swaps stick (only-on-change).** Service ON, no display-carrying
    context active, Tideo's display fields at defaults. Toggle Night Light by hand in the system QS;
    let context swaps happen whose profiles don't differ in display fields. **Expected:** your
    manual state survives — equal swaps write nothing, and the system's own Night Light schedule
    keeps working while Tideo's fields stay default.
36. **No-op below ELEVATED.** Revoke:
    `adb shell pm revoke com.tideo.autobrightness android.permission.WRITE_SECURE_SETTINGS`.
    **Expected:** the Menu's "Privileged" row disappears on the next resume; the screen (if open)
    falls back to the 3-channel grant card; a context swap carrying display fields writes
    **nothing** (device toggles untouched; super dimming also inert — same grant). Re-grant →
    toggles assert again from the next change, no restart needed.
37. **D-151 accepted residual (process death mid-override).** With a context-loaded profile holding
    grayscale ON, kill the process: `adb shell am force-stop com.tideo.autobrightness`; let the rule's
    window lapse while the app is dead. **Expected:** grayscale REMAINS on the device — there is
    deliberately no latch or residual sweep (D-151 trade). Start the service again. **Expected:** the
    seed adopts silently (grayscale still on); the next **differing** profile swap or a service stop
    returns the device to the baseline's values. Verify the self-heal happens then — this residual is
    as-designed, not a bug.
38. **Circadian temperature tracking (D-154).** On Privileged Display enable Night Light + **Follow
    circadian scaling**, Apply (baseline), service ON, ideally within ~1 h of local sunset/sunrise.
    **Expected:** within a minute the temperature starts moving with the sun —
    `adb shell settings get secure night_display_color_temperature` drifts toward your slider value
    (warmer) as the evening ramp progresses, and toward 4082 in daylight; in stable indoor light too
    (the ticker is independent of brightness cycles). Change the temperature by hand in system
    settings. **Expected:** it is re-overridden within ~1 min — documented behavior while tracking is
    on (every other display field keeps manual changes). Turn the switch off + Apply. **Expected:**
    the ticker stops and the temperature returns to the profile's static value (the slider; with the
    slider unset it simply stays where the ramp left it); manual changes stick again.
39. **Panic resets the privileged keys (D-155).** With a profile holding grayscale + inversion +
    Night Light engaged (via context rule or Apply), fire the panic gesture (step 14).
    **Expected:** besides the SOS + max brightness + service stop, ALL display toggles return to
    **defaults** (color back, inversion off, Night Light off, AOD/stay-awake off, HDR re-allowed)
    — including a pre-existing residual (repeat after a force-stop mid-override: panic still
    clears it). Re-enable the service. **Expected:** the baseline's display fields re-assert on
    start — panic is an escape hatch, not a permanent opt-out.

## 12. Accessibility — TalkBack & touch targets (D-156) — NEW 1.8.0

The a11y backlog (D-156, units A0–A7) is verified in CI by the `SemanticsAudit`
gate + the `TouchTargetsA11yTest` floor, but semantics tests only *approximate* TalkBack, and Compose's
runtime `minimumInteractiveComponentSize()` expansion is **not observable in Robolectric** — so the two
checks below are **owner-verified on-device** (no emulator/KVM). Turn TalkBack on:
Settings → Accessibility → TalkBack → On (or hold both volume keys). Swipe right/left to move the focus,
double-tap to activate.

40. **Every control announces a meaningful name (TalkBack).** Swipe through, in turn: the Dashboard
    (master switch says "Auto brightness service, switch"; the amber Stale / Override / resume banners
    are **announced automatically** when they appear — don't have to be focused), the Menu rows, and a
    representative settings screen of each kind — a slider screen (Curve/Reactivity), a switch-heavy
    screen (Misc/Super Dimming), Tools, Privileged Display (at ELEVATED), and the Contexts rule editor.
    **Expected:** no control focuses as "unlabeled", "button", or a bare symbol; each slider/switch reads
    its field name; the ⓘ help buttons read "Help: <field>"; section headers are reachable via TalkBack's
    heading navigation (swipe up/down with the rotor on "Headings"); the app-picker checkboxes read their
    app name. The chart screens' graphs read a one-sentence summary (e.g. "Brightness curve graph, …");
    the pager ‹ › read "Previous/Next chart".
41. **Touch targets are reachable (Switch Access / large-finger).** Enable Settings → Accessibility →
    Switch Access (or just verify by touch): the primary tap affordances — nav rows, cards, the ‹ › chart
    arrows, Apply/Discard, the back arrow, and the M3 sliders/switches/checkboxes — are each **≥ 48 dp**
    and comfortably hittable. **Known/accepted residual:** the chart **pager position dots** are small
    (~8–10 dp) *indicators*, not a primary control — page with the 48 dp arrows or a horizontal swipe
    instead (they are excluded from the automated floor by design — `TouchTargetsA11yTest`).

## 13. Automation control — intent surface (D-157) — NEW 1.8.0

Opt-in external control (Tasker / MacroDroid). CI covers the gate + verb routing + the outbound event
contract (`ControlReceiverTest`, `AmbientMonitoringServiceTest`), but end-to-end delivery from a real
automation app and the `SERVICE_ON` background-start behavior are **owner-verified on-device**. Full
reference: [`docs/AUTOMATION.md`](../AUTOMATION.md). Use `adb` (no automation app needed) unless noted.

42. **Off by default ⇒ commands ignored.** With **Tools → Automation control** OFF, send
    `adb shell am broadcast -a com.tideo.autobrightness.control.SERVICE_ON -n com.tideo.autobrightness/.app.control.ControlReceiver`.
    **Expected:** nothing happens (service stays off). Turn the toggle ON, resend. **Expected:** the
    service starts (Dashboard shows it running).
43. **Core verbs route.** With the toggle ON and the service running, send `PAUSE`, then `RESUME`, then
    `REAPPLY`, then `PANIC` (same action namespace/component). **Expected:** pause holds brightness,
    resume re-adapts, reapply recomputes now, panic restores brightness + stops (like the notification
    **Reset**). `SERVICE_TOGGLE`/`SERVICE_OFF` flip/stop the service. Then, with the service turned
    **off** (master switch off, automation toggle still ON), send `RESUME`. **Expected:** nothing
    happens — an external RESUME never overrides the master switch (D-160).
44. **`LOAD_PROFILE` + resume.** Send `LOAD_PROFILE` with `--es name "Night"` (a real saved profile).
    **Expected:** that profile loads and the manual lock latches (Dashboard shows the profile);
    `CONTEXTS_RESUME` clears it and hands control back to context rules. An unknown `name` is a no-op.
45. **`SERVICE_ON` while not running.** With the service OFF (but the toggle ON), send `SERVICE_ON`.
    **Expected:** it starts. If the device is aggressive about background starts and it does **not**
    start (Dashboard shows *degraded*), exempt Tideo from battery optimization and retry — it should
    then start (documented caveat).
46. **Outbound `STATE_CHANGED` events.** Register for `com.tideo.autobrightness.event.STATE_CHANGED`
    (Tasker *Intent Received*, or `adb shell dumpsys` / a logging receiver). Toggle the service, pause,
    resume, load a profile. **Expected:** an event fires on each change carrying
    `enabled`/`running`/`paused`/`profile`; a final `enabled=false` event fires when the service stops.
    With the Automation-control toggle OFF, **no** events are emitted.

## 14. Edge-to-edge + keyboard insets (D-159) — NEW 1.8.0

`MainActivity` now calls `enableEdgeToEdge()` **app-wide** (plus manifest `adjustResize` and the
Scaffold-level `imePadding()`), which changed how EVERY screen receives system-bar and keyboard
insets on API 31–36. CI cannot see insets — this sweep is the only real gate. Test with **gesture
navigation** first, then repeat the marked items with **3-button navigation** (taller nav bar).

47. **Draft-screen keyboard lift (the D-159 bug itself).** On Curve & Brightness, focus the LAST
    field of the scroll and type. **Expected:** the keyboard opens, the sticky Discard/Apply bar sits
    directly ON TOP of the keyboard (lifted once, not twice), and there is NO keyboard-tall empty gap
    between the last field / bar and the keyboard. Scroll the list while the keyboard is open — the
    content ends at the bar, no dead zone. Repeat on Super Dimming ("Circadian dim spread", the
    original repro field). *(both nav modes)*
48. **Dialog editors unaffected.** Open a Contexts rule editor (Dialog window, D-098) and focus a
    text field. **Expected:** Save/Cancel stay visible above the keyboard exactly as in 1.7.0 —
    the activity-level edge-to-edge change must not alter Dialog insets.
49. **All-screens insets spot-sweep.** Visit Dashboard → Menu → each settings screen → Tools →
    Profiles → About/Guide. **Expected:** no content under the status bar, no bottom controls clipped
    by the nav bar (D-100 class), no newly doubled top/bottom padding anywhere. *(both nav modes)*
50. **Keyboard on non-draft surfaces.** Profiles screen → "Save current as…" name field; Contexts
    SSID field. **Expected:** the focused field stays visible above the keyboard; no pan-jump of the
    whole window (adjustResize + inset dispatch, not legacy ADJUST_PAN).

---

**On completion:** flip the affected `PARITY_CHECKLIST.md` rows to `device-verified`; record any failures
in `STATE.md` → "Gate findings" for a punch-list session. Gate 3 pass → bump `versionName` to `1.0.0`.
