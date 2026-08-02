# Device test script — everything on this branch that `main` does not have

Covers the **full `origin/main..HEAD` diff** for the 1.8.2 / vc20 train, organised by what a person
can actually observe on a phone. Supersedes the two per-round scripts from this train.

> **Ephemeral (DB-010).** This file dies when 1.8.2 ships: anything with standing value gets folded
> into the numbered sections of `DEVICE_TEST_SCRIPT.md` and this file is deleted (RUNBOOK §6). The
> permanent regression sweep is `DEVICE_TEST_SCRIPT.md`; this one exists so the owner isn't re-running
> the whole app to check one train's changes.

**Not a regression pass.** Brightness curves, profiles, context rules, the wizard, graphs, tile,
widget and notification are untouched by this train and are covered by the JVM/Robolectric suites —
they are deliberately absent below. Everything here is something this branch *changed*, and most of
it is something no CI in this repo can reach.

**Build:** `app-debug.apk`, versionName **1.8.2-debug**, applicationId
`com.tideo.autobrightness.debug` — installs alongside a release install and keeps its own data.

```bash
adb install -r app-debug.apk
```

Shorthand used throughout:

```bash
PKG=com.tideo.autobrightness.debug
CTL=$PKG/com.tideo.autobrightness.app.control.ControlReceiver
P=com.tideo.autobrightness.control
```

---

## A. Smoke — the toolchain changed underneath everything (DA-026)

The AGP 8.7.3 → 8.13.2 bump rebuilt `classes*.dex` and the baseline profile. No behaviour should
change, which is exactly why a shallow pass matters before the specific tests.

1. Launch, grant `WRITE_SETTINGS` if prompted, enable the service.
2. Cover and uncover the light sensor. **Pass:** brightness tracks as it always did.
3. About screen reads **1.8.2**.

**Fail signal:** anything that smells like a dexing/startup problem — crash on launch, missing
screens, slow cold start. That would be the AGP bump, not the features below.

---

## B. Issue #110 — the dimming-strength field stops lying (DB-008)

65 was always the effective ceiling; the field kept showing whatever you typed.

1. Super Dimming → **Strength Setpoint = 100** → **Apply**.
   **Pass:** a message says it was reduced to 65 **and the field now reads 65**.
2. Leave the screen and return. **Pass:** still 65 — it persisted and the screen is not stuck dirty.
3. **64** → Apply. **Pass:** stays 64, no message.
4. **65** → Apply. **Pass:** stays 65, **no message** (matches upstream's `> 65.0000000001`).
5. **The reporter's own measurement** — confirm the change was cosmetic at the value level:
   ```bash
   adb shell settings get secure reduce_bright_colors_level
   ```
   Strength 65, threshold 1, circadian spread 0, dark room. **Pass:** the level behaves as before this
   train. **Fail:** dimming got *weaker* — the clamp was only ever supposed to correct the display.

## C. Issue #110 — panic gesture "only when plugged in" (DB-009)

New switch on **Live Debug**, under Panic Sensitivity. Default **off**.

1. **Off, on battery:** unplug, screen on, flip upside down and shake.
   **Pass:** panic fires (SOS vibration, brightness 255, service stops).
2. **Toggle ON, still unplugged:** gesture again. **Pass:** nothing happens.
3. **Plug in, gesture again** — without locking the screen in between.
   **Pass:** fires. (This checks the toggle takes effect immediately rather than at the next
   screen-off, which needed its own wiring.)
4. **Unplug, toggle still ON:** gesture. **Pass:** nothing.
5. **Toggle back OFF while unplugged:** gesture. **Pass:** fires, again with no screen-off needed.

## D. Panic gesture survives the new sensor gating (DB-009)

The accelerometer is now released when the screen is off and re-registered on screen-on. My first
version made the gesture need an extra flip afterwards, so check this directly:

1. Service running, toggle **off**. Lock the screen, wait ~10 s, unlock.
2. **Immediately** flip upside down and shake — no preparatory flip-straight-and-back.
   **Pass:** fires first time. **Fail:** needs a second flip to arm.
3. Screen off, shake it upside down in your hand, then unlock.
   **Pass:** brightness untouched, service still running (it never could fire with the screen off).

## E. Battery — the point of the sensor change (DB-009)

Before this train the accelerometer ran at ~50 Hz for the life of the service, including all night.

```bash
adb shell dumpsys sensorservice | grep -i -A3 tideo
```

- Screen **on**, gesture available → Tideo holds an accelerometer connection.
- Screen **off** → **no** Tideo connection. This is the fix.
- Plugged-only toggle ON while unplugged → **no** connection even with the screen on.

Softer signal over a normal day: Settings → Battery → Tideo, screen-off portion should drop versus
what you are used to.

---

## F. Extra Dim failure handling (DA-038, DB-001) — **ELEVATED only**

A failed level write used to leave the *previous, stronger* level on screen while logging `ON`.

1. Enable super dimming, dark room, confirm it engages:
   ```bash
   adb shell settings get secure reduce_bright_colors_activated   # 1
   adb shell settings get secure reduce_bright_colors_level       # note it
   ```
2. Revoke mid-flight: `adb shell pm revoke $PKG android.permission.WRITE_SECURE_SETTINGS`
3. Brighten the room (or raise min brightness) so a **weaker** level is requested.
   ```bash
   adb shell settings get secure reduce_bright_colors_activated   # expect 0
   ```
   **Pass:** Extra Dim ends **off**, not stuck at the old stronger level. With debug category 5 on,
   the flash reads `FAILED … Extra Dim cleared`, never `ON`.
4. Restore: `adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS`

**Teardown (DA-038):** with dimming engaged, disable the service from the notification.
**Pass:** Extra Dim clears and the brightness mode returns to what you had (auto/manual), rather than
leaving the screen dimmed and stuck in manual.

## G. Profile import boundaries (DA-029, DA-036, DA-044)

1. **Normal round trip:** Profiles → export to Files → import it back. **Pass:** settings restored.
2. **Oversized file** — 300 KiB of junk exceeds the 256 KiB budget:
   ```bash
   adb shell "yes x | head -c 300000 > /sdcard/Download/big.json"
   ```
   Import it. **Pass:** a *distinct* "too large" message — not the generic unreadable one.
3. **Corrupt file:** `adb shell "echo 'not json' > /sdcard/Download/bad.json"` → import.
   **Pass:** an "unreadable/could not parse" message, different from the oversize one.
4. **Slow provider (DA-044)** — the UI-thread fix. Pick a file from a cloud storage app with
   Wi-Fi/data turned off mid-pick. **Pass:** the app stays interactive (you can scroll and press
   Back) and eventually shows an import error, within ~20 s. **Fail:** the UI freezes.

## H. Sticky-restart gate (DA-030)

The service must not resurrect against a persisted disable when Android restarts it.

1. **Disabled:** turn the service off, then `adb shell am force-stop $PKG`, wait ~30 s.
   **Pass:** no Tideo notification reappears; `adb shell dumpsys activity services $PKG` shows none.
2. **Enabled:** turn it on, `adb shell am force-stop $PKG`, wait.
   **Pass:** it comes back and resumes tracking brightness.

## I. External control bounds (DA-039, DA-043)

Needs **Tools → Automation control → Allow external control** ON (default off — verify that first:
with it off, `adb shell am broadcast -a $P.SERVICE_OFF -n $CTL` must do nothing).

1. **Flood:**
   ```bash
   for i in $(seq 1 300); do adb shell am broadcast -a $P.REAPPLY -n $CTL >/dev/null; done
   ```
   **Pass:** UI stays responsive, brightness settles once, no long tail of re-applies, no ANR.
2. **Ordering is not sacrificed to the bound:**
   ```bash
   adb shell am broadcast -a $P.PAUSE -n $CTL
   adb shell am broadcast -a $P.RESUME -n $CTL
   adb shell am broadcast -a $P.PAUSE -n $CTL
   ```
   **Pass:** ends **paused**.
3. **Unknown verb does not disturb a real one:**
   ```bash
   adb shell am broadcast -a $P.NOT_A_VERB -n $CTL
   adb shell am broadcast -a $P.PAUSE -n $CTL
   ```
   **Pass:** the PAUSE takes effect.
4. **Unknown service action never starts the runtime (DB-005):**
   ```bash
   adb shell am start-foreground-service -a com.tideo.autobrightness.runtime.action.NONSENSE \
     -n $PKG/com.tideo.autobrightness.app.runtime.AmbientMonitoringService
   ```
   **Pass:** no persistent notification, service does not stay up.

## J. Backup and restore (DA-034, DB-002)

The one item with no test-level proof at all — the `BackupAgent` only runs during a real restore.

```bash
# With the service RUNNING and a profile manually loaded (context lock latched):
adb shell bmgr backupnow $PKG
adb uninstall $PKG
adb install -r app-debug.apk
adb shell bmgr list sets          # find the token
adb shell bmgr restore <token>
```

**Pass, all three:** brightness configuration and saved profiles are back; the service is **off**;
the Profiles screen shows context automation active (no manual-lock banner).

> The important case is the *default* one: if you never toggled the service off before backing up,
> the key is absent from the file entirely — which is exactly the case the first version of this fix
> would have missed.

## K. Privileged paths (DA-031, DB-005) — Shizuku / root

1. **Grant reports the truth:** with Shizuku running, Tools → Privileges → Grant via Shizuku.
   **Pass:** the UI says granted **and** so does
   `adb shell dumpsys package $PKG | grep WRITE_SECURE_SETTINGS`. (Before this train a grant that
   printed any output was reported as failed — the interesting signal is the two agreeing.)
2. **Prompt dismissal terminates:** trigger the grant, swipe the Shizuku dialog away without
   answering. **Pass:** an error appears within ~2 min and the button works again. **Fail:** hangs.
3. **Double-tap:** tap Grant twice quickly. **Pass:** the second says a grant is already in progress;
   no duplicate prompts.
4. **Force dark** (Tools): toggle on/off. **Pass:** takes effect and survives a service restart.
5. **Wi-Fi SSID context rule without Location:** if you use one, confirm it still matches.

## L. Geo-IP (DA-037, DB-006) — opt-in

1. **Default off:** fresh install, no consent given. **Pass:** no network request; circadian uses
   fixed/default windows.
2. **Manual request:** enable consent, tap the manual location request. **Pass:** coordinates
   resolve, circadian windows update.
3. **Daily bound:** the automatic attempt happens at most once a day even if it fails.
4. **Cancellation (DB-006):** open the screen that triggers a location refresh and leave immediately.
   **Pass:** no stall on return, no delayed toast ~30–60 s later.

---

## What is deliberately not in this script

- **CI and repo plumbing** — the F-Droid compatibility workflow (DA-027/DA-028/DB-003), the
  dependency and wrapper-digest work (DA-040…DA-042), README badges (DA-025), the doc consolidation
  (DB-007) and the container bootstrap revert (DA-032/DA-033). None of it ships in the APK; it is
  verified by CI, and by F-Droid's own rebuild at the next tag.
- **Release-only logging (DA-034)** — release builds no longer send coroutine throwables to logcat.
  Not observable on a debug build by construction.
- **The unchanged app** — curves, profiles, context rules, wizard, graphs, tile, widget,
  notification. Untouched by this train.

## Reporting

```bash
adb logcat -d > logcat.txt
adb shell dumpsys activity services com.tideo.autobrightness.debug
adb shell settings get secure reduce_bright_colors_level
adb shell settings get secure reduce_bright_colors_activated
```

Section letter, what you expected, what happened. The logcat is the tiebreaker.

## Known limits

- The two-rate panic sensor idea (slow orientation watch, 50 Hz only inside the shake window) is
  **not** in this build — the gesture's constants are tuned in frames at ~50 Hz, so changing the rate
  silently changes how it feels. Separate change, separate tests.
- Wireless charging counts as plugged (`EXTRA_PLUGGED > 0`), matching the upstream Java.
- Sections J and K are the two where nothing in CI can substitute for the device.
