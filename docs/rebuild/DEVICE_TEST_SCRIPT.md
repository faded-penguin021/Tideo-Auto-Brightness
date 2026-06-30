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

---

**On completion:** flip the affected `PARITY_CHECKLIST.md` rows to `device-verified`; record any failures
in `STATE.md` → "Gate findings" for a punch-list session. Gate 3 pass → bump `versionName` to `1.0.0`.
