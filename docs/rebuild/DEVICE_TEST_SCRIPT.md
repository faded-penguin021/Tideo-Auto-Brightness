# DEVICE_TEST_SCRIPT — the standing on-device acceptance pass

**The one permanent device script.** Originally the Gate 3 acceptance pass for v1.0.0; it now covers
the whole shipped app and is the regression sweep for a release. Sections are cited by number from
code and from the ledger — **add sections at the end, never renumber**.

Per-round scripts (`DEVICE_TEST_SCRIPT_<version>.md`) are the *other* kind: one at a time, covering
only what an unreleased train changed, deleted once that version ships — with anything worth keeping
folded in here first (RUNBOOK §6).

Run this end-to-end on a real device (no emulator — the SoC has no KVM here, and the light/proximity/
battery sensors, OEM brightness range, Shizuku binder, and doze are only exercisable on hardware). Tick
each step's **Expected**; log any miss in `../STATE.md` → "Owner queue". Build + install the debug APK
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
10a. **No false pause on wake (DB-082, issue #123).** Override Detection on, service running, not
    already paused. **Inject the trigger — do not wait for it.** The app cannot tell who wrote
    `SCREEN_BRIGHTNESS`, so an `adb` write is the same event as the OEM's own wake write; waiting
    for the OEM instead makes this a check that passes on any build (DB-083). Pick a value far from
    the current one. Run the last two lines back-to-back so the write lands inside the 1.5 s window:

    ```
    adb shell input keyevent KEYCODE_SLEEP; sleep 3
    adb shell input keyevent KEYCODE_WAKEUP
    adb shell settings put system screen_brightness 200
    ```

    **Expected:** nothing — no pause, no notification, no Resume card. **Then the control, which is
    the half that matters:** the same two commands with `sleep 5` between them. **Expected:** it
    DOES pause, exactly as step 8. A build that stays quiet for both has not fixed the bug, it has
    disabled override detection, which is the worse defect — treat a silent control as a FAIL.
    Both directions were verified this way on 1.9.2-debug (owner, 2026-08-24).

    Optional, and only on a device already known to re-assert brightness on wake: lock and wake ten
    times touching nothing, expecting no pause. **On any other device this observes nothing** — it
    passes whether the fix is present or absent, so record it as SKIPPED, never as evidence.

10b. **The deadband boundary (DC-005, issue #126).** Injected, so it fails on a device that never
    shows the bug. **Mind the coordinate systems:** `settings put system screen_brightness` takes
    RAW device values, while the app's `lastAppliedBrightness` is DOMAIN 0–255. Never type a domain
    number into these commands.

    **Start each injection unpaused (DC-012).** Live Debug → System Status → "Manual override" must
    read `No`; if it reads `Paused`, Resume from the notification first. The latch is sticky — only
    an explicit Resume clears it — and a paused pipeline drops every injected event, so any later
    check passes vacuously. This one's own control pauses on purpose, so re-check between the two
    halves and before 10c.

    First read `deviceMax` from Live Debug → **Brightness Writes** → "Device max"; call it M. That
    is `config_screenBrightnessSettingMaximum`, the value Tideo actually converts with — **not** the
    largest raw value the device will store, which may be smaller and is precisely the suspected
    fault.

    **M is what the app converts with, which is not necessarily what the provider accepts (DC-014).**
    Read M off the card, then measure the provider's real range separately — drag the system
    brightness slider to maximum and run `adb shell settings get system screen_brightness`. On the
    owner's phone the card reads **255** while the slider reports **4095**: the app resolved the AOSP
    default and drives the whole curve inside the bottom 6% of the panel. Where the two disagree,
    these injections are in PROVIDER units while the deadband is in the app's, so record both numbers
    before reading any result of this check.

    **Convert, do not step (DC-010).** Where M ≠ 255 a fixed raw offset is not a fixed domain
    distance, so never inject one. On a hypothetical M = 4095 device a domain step is 16.06 raw, so a
    round `+20` lands 1 domain step from about three quarters of the raw values and 2 from the rest,
    a single step of `+16` quantises to 0 at 14 of them and tests nothing, and even a doubled `+32`
    quantises back to 1 at 28 of them — which would FAIL a correct build. Pick the raw value for the
    domain value you want; at M = 255 the conversion is the identity and `raw(n) = n`. With the
    service running and one cycle completed:

    ```
    adb shell settings get system screen_brightness   # current raw, call it R
    # d      = round(R × 255 / M)   <- the domain value the app sees
    # raw(n) = round(n × M / 255)   <- the raw value that lands on domain n
    adb shell settings put system screen_brightness <raw(d + 1)>
    ```

    **Expected:** NO pause — one domain step is representational drift and the ±1 deadband is
    inclusive. **Then the control:** `raw(d + 2)`. **Expected:** it DOES pause. A build quiet for
    both has disabled detection, not fixed anything — FAIL it.

    **Record the disposition after each half, not just whether it paused (DC-015).** Live Debug →
    **Brightness Writes** → "Last override" and "Override seen". A quiet half is two different
    events wearing one face: a fresh `DISMISSED_DRIFT` means `handleOverride` ran and judged it,
    while a stale or absent timestamp means the monitor never delivered the event at all — a closed
    gate, the F64/DB-082 settle window, or the DC-003 self-write adoption. Only the first is this
    check passing. **NOT yet verified on a device:** the 2026-08-30 round was read under a wrong M
    and is being re-run (STATE Owner queue).
10c. **Mode conflict dismisses instead of pausing (DC-006, issue #127).** Service running, override
    detection on, and **not already paused** — check "Manual override" as in 10b (DC-012).

    ```
    adb shell settings put system screen_brightness_mode 1
    adb shell settings put system screen_brightness <a raw value far outside the deadband>
    ```

    **Expected:** NO pause, no "manual override" notification, and the app flips the mode back —
    `adb shell settings get system screen_brightness_mode` reads `0` within a cycle. **That `0` does
    not on its own prove Tideo did it (DC-011):** an OEM build may clear the mode on any manual
    `screen_brightness` write, and then the expected observation arrives whether or not the reclaim
    ran. **The distance is what separates them (DC-013)** — do not use a nearby value. Far outside
    the ±1 deadband the two explanations predict opposite outcomes: had the OEM cleared the mode
    first, Tideo would read MANUAL, fail the drift test and PAUSE. So a quiet run at that distance
    can only be the mode branch, and Live Debug → **Brightness Writes** → "Last override" reading
    `DISMISSED_MODE` confirms it directly. A quiet run at a NEARBY value proves nothing either way
    (the deadband would dismiss it regardless) — record that as SKIPPED. **Control:** with the mode
    already `0`, the same brightness write MUST pause as in step 8. Verified on 1.10.0-debug vc24
    (owner, 2026-08-30): mode 1 + raw 4000 on an M = 4095 panel, quiet.
10d. **Normalization readout (diagnostic, not pass/fail).** Live Debug → **Brightness Writes**, at
    the TOP of the curve (bright room, or raise Min/Max Brightness so a high value is written). Let
    one cycle complete; this needs no override to have fired. Record "Requested → acknowledged",
    the status, and "Device max". **A requested value above the acknowledged one at the top of the
    range means the advertised maximum disagrees with what the provider stores, and the top of that
    user's curve is silently flat.** Report the three numbers; do not change any setting to "fix" it.

    **Add a fourth number, and take it from the system, not from adb (DC-014).** Drag the system
    brightness slider to maximum, then `adb shell settings get system screen_brightness`. This is the
    provider's real ceiling, and it is the one number the app cannot get wrong by misreading a
    resource: an `adb settings put` can be clamped or overwritten by auto-brightness, whereas the
    system's own slider writes what the platform believes its range to be. **"Device max" below this
    is the defect, not a rounding question** — Tideo then converts with the smaller number, its whole
    0–255 domain lands inside the low end of the panel, and `toDomain` clamps everything above the
    advertised max to 255, so override detection goes blind across the rest of the slider. On the
    owner's phone (2026-08-30) the card read 255 against a slider maximum of 4095.

## 3. Screen off/on — hibernate & reinit (prof753/585, prof761/618)

11. Turn the screen off, wait ~10 s, turn it on. **Expected:** sensing resumes; an initial brightness is
    set for the ambient level; context automation resumes (manual lock cleared on wake).
12. Reboot the device. **Expected:** the service self-starts (foreground notification returns) if it was
    enabled (specialUse FGS is boot-eligible).

## 4. Proximity damp (prof759/task545)

13. With the service running in changing light, cover the **top** of the phone (proximity "near", e.g.
    hold it to your ear). **Expected:** brightness reactivity is **damped** (changes ~10× slower) but the
    loop does **not** pause; uncovering restores normal reactivity. (Live Debug LuxAlpha drops while near.)

## 5. Panic reset (prof769/task528)

14. Hold the phone **upside down** (charging port up) and **shake** vertically. **Expected:** an **S.O.S.
    vibration**, brightness forced to **maximum**, the service stops (full reset).
14a. **Every panic entry point confirms (DB-037).** Repeat the reset twice more without the gesture:
    once via the foreground notification's **Reset** action, and once via the intent surface
    (`adb shell am broadcast -a com.tideo.autobrightness.control.PANIC -n com.tideo.autobrightness/.app.control.ControlReceiver`,
    automation toggle ON — §13). **Expected:** the same S.O.S. vibration both times; before DB-037
    only the gesture buzzed. Then fire the gesture once more and confirm it still vibrates **exactly
    once** — the call moved to the shared path, so a leftover call site would double-buzz.
15. **Grab the phone out of a pocket and turn the screen on normally** (do not deliberately invert+shake).
    **Expected:** panic does **NOT** fire — the 3 s post-wake grace + the stricter inversion threshold
    suppress the grab-to-wake false trigger.
15a. **"Only when plugged in"** (Live Debug, under Panic Sensitivity; default **off**, DB-009).
    Unplugged with it **on**, gesture. **Expected:** nothing. Plug in — without locking the screen —
    and gesture. **Expected:** fires, so the toggle takes effect immediately rather than at the next
    screen-off. Unplug and gesture again: nothing.
    **Run that last step twice**: once continuing from the plugged case, and once after stopping and
    re-enabling the service while unplugged. The second shape is the one that broke (DB-011) — the
    gesture started before the settings snapshot resolved and an unresolved snapshot read as "no
    restriction".
15b. **The accelerometer is released while the screen is off** and re-registered on screen-on
    (DB-009 — it was held at ~50 Hz for the life of the service, including screen-off, where the
    gesture cannot fire). With the toggle off: lock, wait ~10 s, unlock and **immediately** gesture.
    **Expected:** fires first time. **Fail:** it needs a preparatory flip-straight-and-back to arm.
    Then shake it upside down with the screen off and unlock. **Expected:** brightness untouched,
    service still running.

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
19a. **Strength setpoint is clamped where it is stored, not only where it is used** (DB-008).
    65 was always the effective ceiling, but the field kept showing whatever was typed. Set
    **Strength Setpoint = 100** → **Apply**. **Expected:** a message says it was reduced to 65 **and
    the field reads 65**; leave the screen and return, still 65. **64** → Apply: stays 64, no message.
    **65** → Apply: stays 65 and **no message** (the announcement fires only when the value actually
    moved). Then confirm the correction was cosmetic at the value level —
    `adb shell settings get secure reduce_bright_colors_level` at strength 65, threshold 1, circadian
    spread 0, dark room. **Fail:** dimming got *weaker*; the clamp was only ever meant to correct the
    display.
19b. **A grant made while the app is running is picked up without a restart** (DB-012). With the
    service running and the screen on, grant `WRITE_SECURE_SETTINGS` over adb. **Expected:** the tier
    badge reaches **ELEVATED** within ~10 s and super dimming starts working, with no app restart.
    **Known residual:** `PrivilegeManager` is per-`AppModule` and `AppModule` is built at ~10 call
    sites, so the tier cache is shared only within one instance; DB-012 self-heals the visible symptom
    rather than making it process-wide.
19c. **A failed Extra Dim level write does not leave the previous, stronger level on screen**
    (DB-001). Hard to force deliberately — if a level write ever fails (revoked grant,
    SettingsProvider error) while dimming is engaged, **Expected:** the app deactivates and re-engages
    from scratch on the next cycle, and the debug line reports `ON <level>` only for a write that
    actually landed.

## 7. Circadian scaling (task90)

20. On the Circadian screen, enable dynamic scaling; check the chart's **"Now"** line and the live curve.
    **Expected:** the scale multiplier tracks the real local sunrise/sunset (not a fixed UTC window).
21. Set a **fixed date/location** (Experiment element). **Expected:** the curve + the live scaling shift
    to that day/place; "Use live data" reverts.
21a. **Live date with a fixed location (DB-084).** From the state left by step 21, tap **Live date**,
    then **Set fixed**. **Expected:** the status line reads "Fixed location: … (live date)" — not
    "Fixed: <a date> @ …" — and the date button shows today with "(live)". Now enter a far-southern
    location (Sydney, `-33.87` / `151.21`) and Set fixed again: sunrise/sunset on the curve jump to
    Sydney's, and in northern-hemisphere summer the daylight window becomes the SHORT one, which is
    only possible if today's date is still in play. Pinning a date as well (step 21) and clearing the
    coordinate fields must still give the date-only case, so all three combinations are reachable.

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
      **Run this on Android 12 or 12L if you have one (DB-074):** both of those paths ran through an
      API-33 call whose `NoSuchMethodError` was swallowed, so they returned nothing at all on API
      31/32 while the Shizuku path kept working. A blank field there, with the grant in place, is
      the regression. Nothing below API 33 is reachable from a JVM test — `:platform`'s lint gate,
      not a fixture, is what stands behind this.
24a. **"Use current location", both callers (DB-057…DB-061).** In a Contexts **location rule** tap
    **Use current location**. **Expected:** a toast reading *"Acquiring location — this can take up
    to 45 seconds…"* with the number present, then the two fields filling plus a coordinates toast,
    or a plain failure toast. **Any crash is a regression** — the toast itself once threw
    `MissingFormatArgumentException` (DB-060); if it happens, `adb logcat -b crash` and look for
    `Toaster.invoke`. Repeat on **Menu → Circadian → Use current location**: same number (one shared
    constant, so a wrong number on either screen means the constant broke), and per DB-059 it may
    fill instantly from a last-known fix under an hour old. Then turn the system Location master
    switch **off** and tap the Contexts one again. **Expected:** a *quick* failure, not a 45-second
    hang (DB-057). **Known cosmetic gap, not a FAIL:** the Contexts screen still promises 45 seconds
    before failing fast, where Circadian says "Location is off…" up front.
24b. **A location rule round-trips, in any locale (DB-051/DB-061).** Save a location rule, reopen it.
    **Expected:** the toggle is still on, the coordinates are shown, and the rules list names the
    circle rather than saying "near location". Repeat with the device language set to one using a
    **comma decimal separator** (e.g. Deutsch). **Expected:** identical — the parse/format pair is
    shared by both screens now. Before the fix, Set silently refused and the rule reopened with the
    toggle off.
25. Manually load a profile (Profiles). **Expected:** context automation **pauses** (Resume banner);
    screen off→on or Resume re-enables it.
    - **Resume re-evaluates, it does not reset (DA-018).** With a rule currently MATCHING, load a
      *different* profile by hand, then tap **Resume**. **Expected:** the **rule's** profile becomes
      active (its name shows as the active context and the settings screens show its values) — not
      the hand-loaded one, and not "Default".
    - **Resume with NO rule matching falls back in sync.** Make sure nothing matches, hand-load a
      non-default profile, tap **Resume**. **Expected:** that profile stays active — the label and
      the Curve & Brightness / Reactivity screens **agree**. The 1.8.1 bug was the label flipping to
      "Default" while the settings screens still showed the loaded profile.
    - **[opt]** With External control on, `com.tideo.autobrightness.control.CONTEXTS_RESUME` must
      behave exactly like the banner's Resume.

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

## 11. Privileged Display toggles [ELEVATED] (D-149–D-152)

The toggles are `AabSettings` **profile fields** applied on profile change by
`DisplayTogglesCoordinator` (super-dimming model, idempotent only-on-change); with the service OFF,
Apply writes the device directly (`applyNow`). Debug builds need their own grant
(`… com.tideo.autobrightness.debug …`, D-106).

32. **Each toggle writes + reads back.** Service **OFF** (exercises the direct-write path), on
    **Menu → Privileged → Privileged Display** change one field at a time and **Apply**; confirm the
    device reacts AND the system Settings UI agrees (a wrong key would be silently *created*, not
    rejected — visible agreement is the test):
    - **Night Light** (only shown when Android reports it available) on, temperature slider near 2596 K. **Expected:** screen visibly warms;
      Settings → Display → Night Light shows ON with matching intensity. "Use device temperature"
      (unset) leaves the system's own preference untouched. ⚠️ **Known variance (2026-07-05,
      owner's OnePlus):** OxygenOS ignores `night_display_color_temperature` — the tint is the
      same regardless of the Kelvin value (the switch itself works). The slider and step 38's
      circadian tracking are then visually inert on that device (D-048: documented, not branched;
      the write still lands in the settings table — verify over adb if desired).
    - **Color correction:** Grayscale, then Protanomaly/Deuteranomaly/Tritanomaly. **Expected:** the
      filter matches; Settings → Accessibility → Color correction shows the same mode.
    - **Color inversion** on/off. **Expected:** inverts; the Accessibility toggle agrees.
    - **Always-on display** (only shown when Android reports it available) on/off. **Expected:** AOD appears/disappears on the lock screen.
    - **Stay awake while charging** on, short screen timeout, charger in. **Expected:** the screen
      never sleeps while plugged (AC/USB/wireless/dock); off + unplugged, normal timeout returns.
    - **Disable HDR (experimental, Android 14+)** on → read back
      `user_disabled_hdr_formats=1,2,3,4` and
      `are_user_disabled_hdr_formats_allowed=0`; off → allowed returns `1` and formats clears.
      This writes stored preferences, not Android's Force-SDR service API. Either direction may
      require a reboot, and HDR/display-mode changes may briefly blank the screen (DB-044).
32a. **Stay awake writes AOSP's whole mask, and says so when the device holds another
    (DB-065/DB-068/DB-070/DB-077/DB-078).** Start from OFF or this proves nothing — Apply skips a
    write the device does not need: `adb shell settings put global stay_on_while_plugged_in 0`,
    toggle ON, Apply → **Expected: `15`** (`AC|USB|WIRELESS|DOCK`), not 7. OFF + Apply → `0`.
    Then set a mask this app does not write (`… put global stay_on_while_plugged_in 7` — what
    Tideo itself wrote up to v1.9.0, so this is the state every upgrading device is in; `1` also
    works) and reopen the screen. **Expected:** the switch reads ON *and* a notice appears under it
    saying Android is set to a charger set this app did not write. Now Apply with some *other*
    field changed. **Expected:** the mask stays as you set it — an unrelated Apply must not broaden
    a charger set Tideo did not choose. Finally tap **Use Tideo's setting instead** on that notice.
    **Expected:** the mask becomes `15` and the notice disappears, with no Apply needed. A notice
    that never appears at `7` is the DB-077 regression; a button that needs Apply is DB-078's.
32b. **HDR: an absent row is a default, a partial row is a preference (DB-045/DB-049).** With both
    rows cleared (`adb shell settings delete global user_disabled_hdr_formats` and
    `… delete global are_user_disabled_hdr_formats_allowed`), open the screen. **Expected:** the
    **switch**, off — an untouched device is canonical off. A preservation notice here is the DB-049
    regression. Now produce a *partial* disabled-format set through **Developer options → Disable
    HDR formats**, ticking one or two formats — **never by writing the row by hand**, which is the
    DB-071 ban and not a preference between two allowed routes. **Expected:** the
    switch is replaced by a custom-preference notice, and applying an unrelated field leaves that
    row untouched instead of broadening it to the full set.
32c. **An unrecognised colour-correction mode is READ-ONLY territory (DB-066/DB-069/DB-071).**
    **Never `settings put` a daltonizer value no AOSP path writes** — that class has black-screened
    an owner device. Read only:
    `adb shell settings get secure accessibility_display_daltonizer` (+ `…_enabled`). Values
    `-1, 0, 11, 12, 13` or unset → **SKIP, nothing to observe**, the expected outcome on
    AOSP-faithful hardware. *Only* if a device reports something else on its own: the chips carry a
    preservation notice, an unrelated Apply leaves both keys untouched, an explicit chip pick still
    writes, and **Use Tideo's setting instead** on the notice writes the mode the chips currently
    show without an Apply (DB-078 — that button is the only way to reach the already-selected chip,
    which at a default profile is Off). Otherwise this behaviour is unit-tested only and an accepted
    unverified residual.
32d. **Unsupported Night Light / AOD fail closed (DB-041…DB-043).** Needs hardware that reports the
    feature unavailable — **BLOCKED on every device to hand**, so expect to skip. Where such a
    device exists: the row is hidden entirely (AOD needs `config_dozeAlwaysOnDisplayAvailable` AND a
    non-empty `config_dozeComponent`), and a profile swap, circadian tick, Apply and panic each
    no-op on that field — no write, no crash, no UI claiming a value it cannot hold.
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
    **Do not expect to reach the write-failed banner this way (DB-072):** returning to the app
    re-probes the tier, so the grant card replaces the toggles before there is an Apply to press.
    The banner needs the grant to die *between* opening the screen and applying — a race that
    cannot be staged by hand, and is unit-tested instead.
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
    **defaults** (color back, inversion off, supported Night Light/AOD off, stay-awake off)
    — including a pre-existing residual (repeat after a force-stop mid-override: panic still
    clears it). Re-enable the service. **Expected:** the baseline's display fields re-assert on
    start — panic is an escape hatch, not a permanent opt-out.
39a. **The screen shows the DEVICE, not the stored profile (DB-034).** With Privileged Display open,
    flip Night Light (or color inversion) from the system quick-settings tile, then return to Tideo.
    **Expected:** on resume the toggle shows the device's state, and Apply becomes available because
    the draft now differs from the saved profile — the screen no longer asserts a value the device
    does not hold. **Now flip the SAME toggle back from the tile and return again (DB-039).**
    **Expected:** the screen follows a second time. One external change is not enough to test this:
    the first read-back makes the draft differ from the profile, and the original gate refused every
    change after that, so the feature worked exactly once per screen entry. Now change a toggle
    WITHOUT applying, background the app and return.
    **Expected:** your uncommitted edit survives — a read-back never overwrites a dirty draft.
    Finally enable **Follow circadian scaling** + Apply, service ON, and reopen the screen.
    **Expected:** the temperature slider still shows YOUR value, not the ramp's current Kelvin (the
    ticker owns that key while tracking is on; reading it back would freeze one sample).
39b. **Apply does not undo itself, on either path (DB-047/DB-048).** Service **ON**: open Privileged
    Display, flip Night Light (or inversion), **Apply**. **Expected:** the toggle stays where you put
    it and the Apply bar goes away. A toggle that flips back a moment later — with Apply becoming
    available again — is the read-back rollback: with the service on, the screen does not write the
    device itself, so a pre-Apply snapshot that stayed mergeable replayed over your change. Repeat
    with the service **OFF** (the direct-write path) to cover both halves.
39c. **Apply writes only what differs (DB-068).** Service OFF. Leave Night Light ON in system
    Settings *and* in the app, then change only stay-awake and Apply. **Expected:** Night Light does
    not blink off/on and nothing you set outside the app is re-asserted — only the changed field is
    written.
39d. **A charger set Tideo did not write survives a profile swap and a service stop (DB-077).**
    Step 32a covers the Apply path; this is the **coordinator** path, which reads the device's
    stay-awake state first and skips the write when the device is *already* on the side being asked
    for — so a mask this app cannot represent (`7`, `1`, …) is left alone instead of being broadened
    to `15`. The transition has to be **OFF → ON while the device already holds a custom mask**; an
    ON → ON swap writes nothing on any build and so proves nothing. Prepare two saved profiles,
    **P-off** (stay-awake OFF) and **P-on** (stay-awake ON). Master switch on, **P-off** active, then
    put the device in the state every pre-v1.9.0 upgrade is in, behind the app's back:
    `adb shell settings put global stay_on_while_plugged_in 7`. Switch to **P-on** and read back
    `adb shell settings get global stay_on_while_plugged_in`. **Expected: `7`** — the device was
    already staying awake, so nothing is written and the owner's charger set survives; **`15` is the
    pre-fix regression.** Control, so that cannot pass vacuously: `… put global
    stay_on_while_plugged_in 0`, switch to **P-off** and back to **P-on**. **Expected: `15`** — a
    device that really is off still gets written, so the skip is conditional and not a dead branch.
    Finally the service-stop path: with **P-on** active set `7` again and turn the **master switch
    off** (baseline restore). **Expected:** the baseline's own stay-awake value decides — a
    stay-awake-ON baseline leaves `7` untouched, a stay-awake-OFF baseline writes `0`; both are
    correct, and what must not happen is `7` → `15`. Untouched on purpose: **Use Tideo's setting
    instead** (DB-078) and panic reset still replace the mask outright, so an unexpected `15` is
    worth blaming on an Apply or a panic before the coordinator.

## 12. Accessibility — TalkBack & touch targets (D-156)

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

## 13. Automation control — intent surface (D-157)

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
44a. **Dropped commands explain themselves at level 8 (DB-035).** Set **Live Debug Info → Log Level**
    to **8 — Context Automation**. With the automation toggle **OFF**, send any verb. **Expected:** a
    flash naming the off toggle — and the command still does nothing (the flash must not weaken the
    gate). Turn the toggle ON, then produce each remaining case: `LOAD_PROFILE` with no `--es name`;
    `LOAD_PROFILE --es name "NoSuchProfile"` (the flash quotes the name back); and `RESUME` with the
    master switch off. **Expected:** one flash each, and a verb that *works* (e.g. `REAPPLY`) flashes
    nothing. Now set the level back to **0** and repeat the toggle-OFF send. **Expected:** silence —
    the default configuration keeps D-157's "no side effect before the opt-in gate".
    Deliberately unreported even at level 8: a mistyped action (refused before anything runs) and a
    command dropped by the one-at-a-time admission gate.
45. **`SERVICE_ON` while not running.** With the service OFF (but the toggle ON), send `SERVICE_ON`.
    **Expected:** it starts. If the device is aggressive about background starts and it does **not**
    start (Dashboard shows *degraded*), exempt Tideo from battery optimization and retry — it should
    then start (documented caveat).
46. **Outbound `STATE_CHANGED` events.** Register for `com.tideo.autobrightness.event.STATE_CHANGED`
    (Tasker *Intent Received*, or `adb shell dumpsys` / a logging receiver). Toggle the service, pause,
    resume, load a profile. **Expected:** an event fires on each change carrying
    `enabled`/`running`/`paused`/`profile`; a final `enabled=false` event fires when the service stops.
    With the Automation-control toggle OFF, **no** events are emitted.

## 14. Edge-to-edge + keyboard insets (D-159)

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

## 15. Force dark via Shizuku/root (D-172)

Global `debug.hwui.force_dark` toggle in Tools. Live paths try Shizuku (**running and
authorized**) first, then a root shell; the switch itself always persists.

51. **Toggle applies.** Shizuku running → Tools → "Force dark (Shizuku/root)" → switch ON.
    **Expected:** toast "Applied — fully re-open an app to see it"; status line flips to
    "Currently active on this device."; a fully re-opened light-theme-only app (e.g. Bandcamp)
    renders dark. An app that was already running stays light until swipe-killed and re-opened.
    On a rooted device, repeat with Shizuku stopped — the root fallback (one `su` prompt on
    first use) must behave identically.
52. **Toggle reverts.** Switch OFF, swipe-kill + re-open the same app. **Expected:** light again;
    status "Currently inactive on this device."
53. **No privileged shell** (unrooted, or root denied). Stop Shizuku, re-enter Tools.
    **Expected:** after the probe (~4 s max) the gold "Neither Shizuku nor root is available…"
    line shows; flipping the switch still persists and toasts "Saved — will apply once Shizuku
    or root is available". No crash, no hang.
54. **Reboot re-assert.** Switch ON → reboot → start Shizuku → start the Tideo service (or toggle
    it off/on). **Expected:** `adb shell getprop debug.hwui.force_dark` reads `true` again without
    touching the Tools switch; a re-opened target app is dark.
55. **Opt-out leaves the prop alone.** Switch OFF (opt-out), set the prop by hand
    (`adb shell setprop debug.hwui.force_dark true`), restart the service. **Expected:** the prop
    stays `true` — Tideo never writes it while the opt-in is off.

---

**On completion:** flip the affected `PARITY_CHECKLIST.md` rows to `device-verified`; record any failures
in `../STATE.md` → "Owner queue" for a punch-list session.
