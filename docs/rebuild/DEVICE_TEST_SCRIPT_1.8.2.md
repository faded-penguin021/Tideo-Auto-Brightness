# Device test script — everything on this branch that `main` does not have

Covers the **full `origin/main..HEAD` diff** for the 1.8.2 / vc20 train, organised by what a person
can actually observe on a phone. Supersedes the two per-round scripts from this train.

> **Ephemeral (DB-010).** This file dies when 1.8.2 ships: anything with standing value gets folded
> into the numbered sections of `DEVICE_TEST_SCRIPT.md` and this file is deleted (RUNBOOK §6). The
> permanent regression sweep is `DEVICE_TEST_SCRIPT.md`; this one exists so the owner isn't re-running
> the whole app to check one train's changes.

## Round 2 — what to re-run after the 2026-08-02 pass

The first run was 49 PASS / 5 FAIL / 2 BLOCKED / 3 SKIPPED. Two failures were real defects and are
fixed; the rest were defects in *this script*, whose steps have been corrected in place.

| Re-run | Why |
|---|---|
| **C4** (and C1–C5 around it) | Real bug, fixed (DB-011). The plugged requirement now gates firing, not just sensor registration, and an unresolved settings snapshot no longer reads as "no restriction". |
| **F1–F5** | Real bug, fixed (DB-012). A grant made over adb with the screen on is now picked up within ~10 s instead of needing an app restart. |
| **H1–H2** | The old steps used `am force-stop`, which cancels the very restart they were testing — the FAIL was the script's, and the "PASS" was empty. Method corrected. |
| **G2/G3** | Fixtures need a MediaStore scan to be visible in the picker; the app behaviour they check was fine. |
| **E2** | Now runnable from the phone with a deferred dump. |
| **G4, K2, K3** | Could not create the condition they describe. Corrected, with "record INCONCLUSIVE" where the environment decides. |

Not worth re-running: A, B, D, I, L (all passed and nothing in the fixes touches them).

**Section J is retired — do not run it.** See J for why and what that leaves unverified.

**Not a regression pass.** Brightness curves, profiles, context rules, the wizard, graphs, tile,
widget and notification are untouched by this train and are covered by the JVM/Robolectric suites —
they are deliberately absent below. Everything here is something this branch *changed*, and most of
it is something no CI in this repo can reach.

**Build:** `app-debug.apk`, versionName **1.8.2-debug**, applicationId
`com.tideo.autobrightness.debug` — installs alongside a release install and keeps its own data.

```bash
adb install -r app-debug.apk
```

**Blast radius.** Every remaining command is scoped to `$PKG` and cannot affect other apps. The one
section that was device-wide (**J**, `bmgr`) is retired — it damaged unrelated apps on 2026-08-02 and
is not to be run.

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
   > This is the step that failed on 2026-08-02 (DB-011). Re-run it **twice**: once continuing from
   > step 3, and once after **stopping and re-enabling the service while unplugged** — the second
   > shape is the one that actually broke, because the panic gesture started before the settings
   > snapshot resolved and defaulted to "no restriction".
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

**Measuring the screen-off case from the phone itself** (Termux can't run a command while the screen
is off — 2026-08-02). Queue the dump *before* locking:

```bash
(sleep 20; dumpsys sensorservice > ~/sensors-screenoff.txt) &
# now lock the screen and wait ~30 s, then unlock and read the file
grep -i -A3 tideo ~/sensors-screenoff.txt    # expect NO Tideo accelerometer connection
```

With `dumpsys` unavailable to the shell user, `adb shell` from a computer works the same way.

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
   **Pass (DB-012):** with the screen still on and the app never restarted, dimming re-engages by
   itself within ~10 s of the next cycle in a dark room. On 2026-08-02 it kept reporting the missing
   permission until the app was restarted, because the running service cached its privilege tier and
   only re-detected it at screen-on.

**Teardown (DA-038):** with dimming engaged, disable the service from the notification.
**Pass:** Extra Dim clears and the brightness mode returns to what you had (auto/manual), rather than
leaving the screen dimmed and stuck in manual.

## G. Profile import boundaries (DA-029, DA-036, DA-044)

1. **Normal round trip:** Profiles → export to Files → import it back. **Pass:** settings restored.
2. **Oversized file** — 300 KiB of junk exceeds the 256 KiB budget:
   ```bash
   adb shell "yes x | head -c 300000 > /sdcard/Download/big.json"
   adb shell content call --uri content://media/external/file \
     --method scan_file --arg /sdcard/Download/big.json
   ```
   Import it. **Pass:** a *distinct* "too large" message — not the generic unreadable one.
   > The `scan_file` line is not optional (2026-08-02 run): a file written by the shell is not in
   > MediaStore, and the Downloads picker serves *from* MediaStore — so the fixture is invisible in
   > the picker until it is indexed. That is a test-fixture artifact, not an app defect; moving the
   > file with a file manager works because the manager registers it.
3. **Corrupt file:**
   ```bash
   adb shell "echo 'not json' > /sdcard/Download/bad.json"
   adb shell content call --uri content://media/external/file \
     --method scan_file --arg /sdcard/Download/bad.json
   ```
   → import. **Pass:** an "unreadable/could not parse" message, different from the oversize one.
4. **Slow provider (DA-044)** — the UI-thread fix. Pick a file from a cloud storage app with
   Wi-Fi/data turned off mid-pick. **Pass:** the app stays interactive (you can scroll and press
   Back) and eventually shows an import error, within ~20 s. **Fail:** the UI freezes.
   > **Record INCONCLUSIVE, not FAIL, if the provider answers quickly** (2026-08-02: Drive had the
   > file cached and the import simply succeeded). A fast provider means the slow path was never
   > entered — it is not evidence either way. To force it, pick a large file from an account whose
   > *offline* copy you have cleared, or turn on airplane mode before opening the picker. The 20 s
   > timeout and the off-main-thread read are covered by the automated suite; this step only looks
   > for a UI freeze that no unit test can see.

## H. Sticky-restart gate (DA-030)

The service must not resurrect against a persisted disable when Android restarts it.

> **⚠️ `am force-stop` cannot test this — corrected after the 2026-08-02 run.** Force-stop puts the
> package in the *stopped* state and explicitly cancels pending service restarts, so **neither**
> branch can pass: the enabled case can't come back (reported as a FAIL that was the script's fault),
> and the disabled case "passes" without exercising the gate at all. Use `am crash`, which kills the
> process the way Android's own low-memory kill does, leaving START_STICKY to restart it. It needs a
> debuggable build — this APK is one.

1. **Enabled:** service ON, then
   ```bash
   adb shell am crash $PKG
   ```
   Wait ~30 s (do **not** open the app — opening it starts the service by hand and proves nothing).
   **Pass:** the notification returns on its own and brightness tracking resumes.
   ```bash
   adb shell dumpsys activity services $PKG   # expect a running AmbientMonitoringService
   ```
2. **Disabled:** turn the service OFF, `adb shell am crash $PKG`, wait ~30 s, still without opening
   the app. **Pass:** nothing comes back — no notification, and `dumpsys activity services $PKG`
   shows none. This is the gate: Android restarted the process, and the runtime declined to start
   against the persisted disable.
3. If `am crash` reports the package is not debuggable, the build is wrong — stop and report it
   rather than substituting force-stop.

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

## J. Backup and restore (DA-034, DB-002) — **DO NOT RUN. Owner decision, 2026-08-02.**

This section is retired, not pending. It is kept only so nobody reconstructs it from scratch.

**What happened:** the step as originally written was `adb shell bmgr restore <token>` with no
package argument. That is a **whole-device** restore — it replays the backup set for every package in
it and overwrites the current data of unrelated apps. Run on the owner's daily driver it reset stored
settings across many apps, irreversibly. Scoping it (`bmgr restore <token> $PKG`) fixes the command,
but the owner has declined to re-run this section at all, and that decline stands: **do not re-add
these steps, and do not ask again without new evidence** (`STATE.md` → Decided non-items).

**What that costs, stated plainly:** `SettingsBackupAgent.onRestoreFinished()` never executes outside
a real restore, so nothing in CI or on-device proves it *runs*. What IS proven is the part that
decides the outcome — `SettingsBackupSanitizer` resets `serviceEnabled` and `contextOverride`
unconditionally (including the key-absent case that the first version of the fix would have missed),
covered by unit tests, and the `data_extraction_rules.xml` allowlist is inspectable. The residual
risk is the wiring between them: if the agent were never invoked, a restored install could come up
running against a service the user had switched off. That risk is **accepted** for 1.8.2.

**If it is ever verified, do it on a spare device**, with the package argument, after checking
`bmgr list transports` (a stock device on the Google transport frequently no-ops for a sideloaded
package, which looks identical to "nothing was backed up").

## K. Privileged paths (DA-031, DB-005) — Shizuku / root

1. **Grant reports the truth:** with Shizuku running, Tools → Privileges → Grant via Shizuku.
   **Pass:** the UI says granted **and** so does
   `adb shell dumpsys package $PKG | grep WRITE_SECURE_SETTINGS`. (Before this train a grant that
   printed any output was reported as failed — the interesting signal is the two agreeing.)
2. **Unanswered prompt terminates.** *(Corrected: Shizuku's dialog has no dismiss — Allow or Deny
   only — so "swipe it away" was not a runnable instruction.)* Trigger the grant and, while the
   dialog is up, kill Shizuku from another terminal: `adb shell am force-stop moe.shizuku.privileged.api`.
   **Pass:** Tideo reports an error within ~2 min and the Grant button works again. **Fail:** it hangs
   forever. If you cannot reach a second shell, record BLOCKED — the timeout itself is unit-tested;
   what this step adds is proof the UI recovers.
3. **Double-tap:** tap Grant twice quickly. **Pass:** the second says a grant is already in progress;
   no duplicate prompts.
   > **Needs a fresh authorization to be meaningful** (2026-08-02: "after the first grant/revoke
   > cycle it auto-accepts"). Shizuku remembers the app's authorization, so no prompt appears at all
   > and the step proves nothing. Revoke Tideo in the **Shizuku app → Authorized applications**
   > first, then double-tap. Otherwise record BLOCKED.
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
