# Gate 2 — on-device acceptance (after S12)

This folder is a **throwaway** drop for device testing. The APK and this README live only on the
`claude/magical-feynman-prmdz5` branch and should be **deleted before merging** once you've updated
`docs/rebuild/STATE.md` "Gate findings" and the findings are resolved.

- **APK:** `tideo-auto-brightness-gate2-debug.apk` (debug build, `applicationId com.tideo.autobrightness`)
- Installs alongside nothing else; expect a Google Play Protect "unknown app" warning → Install anyway.
- If you already have a Gate-1 build installed, uninstall it first (or just install over it — same
  app id, settings persist).

## Setup (once)

1. Install the APK and open the app.
2. On first launch you should land on **Setup & Permissions** (tier is NONE):
   - **Allow notifications**.
   - **Modify system settings** (required — this is the BASIC grant that lets it change brightness).
   - *(optional)* **Elevated access** for super dimming — grant `WRITE_SECURE_SETTINGS` via any one of:
     ADB (`adb shell pm grant com.tideo.autobrightness android.permission.WRITE_SECURE_SETTINGS`),
     the **Use Shizuku** button, or **Try root**.
   - **Usage access** step only appears once you've added a per-app context rule (step 7 below).
3. Tap **Done**, then turn the **Auto brightness** master switch ON on the Dashboard.

## What to verify (Gate 2 = surfaces & tiers)

Walk every screen from the Dashboard buttons. For each numeric field: typing should **persist** (leave
and re-enter the screen — value sticks) and an out-of-range / inconsistent value should show a **red
error** (it does NOT block saving — that's intentional Tasker behavior).

1. **Curve & Brightness**
   - Edit min/max/offset/scale, zones, form1A/2B/2C — values persist.
   - Set **Form 2C** larger than **Zone 1 end** → the Form 2C field turns red ("must be ≤ zone1End").
   - The **form2A / form3A** readout updates **live** as you change form1A / zones / form2B.
   - Push the curve so 1000-lux brightness would be very low → a red **safety warning** banner appears.
   - The **brightness curve chart** at the top redraws to match the parameters (log-x lux → brightness).
2. **Reactivity**
   - Edit thresholds; toggle **Detect manual overrides** ON. (Then on the Dashboard, with the service
     running, drag the *Android system* brightness slider mid-animation → it should pause / show the
     override state. This is the Gate-1 F2 item, now enableable.)
3. **Animation & Dimming**
   - Edit animation steps / waits → the **derived throttle** readout updates; set min wait > max wait →
     red error.
   - **Super dimming** rows are **disabled** unless you granted ELEVATED. With ELEVATED granted, enable
     **super dimming** and in dim light (below the threshold) confirm Android **Extra Dim** engages and
     disengages cleanly. (Gate-1 F5 item, now enableable.)
4. **Dynamic Scale** — edit scaling/taper; transition factor > 0.5 and taper midpoint > max brightness
   each show a warning.
5. **Contexts**
   - **Add rule** → name it, pick a profile, set a **time window** and/or **charging only** and/or a
     **Wi-Fi SSID** and/or tick a **foreground app**. Save. The Dashboard "Active context" line should
     reflect the rule when its conditions are met (e.g. plug in the charger for a charging rule; switch
     to the chosen app for an app rule — grant **Usage access** when prompted).
6. **Tools**
   - **Debug** selector shows the 10 named categories; pick one, confirm it persists.
   - **Curve wizard:** with no recorded override points it should report "need ≥ 9 points" (expected —
     override-point capture is not wired yet, noted in STATE.md D-044c). Apply is wired for when points
     exist.
7. **Profiles & Import/Export**
   - **Apply** a built-in profile (e.g. Battery Saver) → settings change.
   - **Export current settings…** to a file, tweak something, **Import** it back → restored.
   - Import a **legacy Tasker** profile export if you have one → values map in.
   - **Reset to defaults** → parameters return to baseline.
8. **QS tile:** add the "Tideo" Quick Settings tile and toggle the service on/off from it.

## After testing

Record pass/fail + any anomalies in `docs/rebuild/STATE.md` → "Gate findings" → "Gate 2". The next
session triages them. Once green, delete this `dist/` folder before merging the branch.
