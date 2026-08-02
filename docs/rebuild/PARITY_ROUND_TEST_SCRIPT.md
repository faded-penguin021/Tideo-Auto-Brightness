# Device test script — DB-008 + DB-009 (issue #110)

Covers only the two parity changes since the last APK: the **dimming-strength setpoint clamp** and the
**plugged-in-only panic gesture** (plus the accelerometer registration change that came with it). Not
a regression pass — brightness curves, profiles and context rules are untouched.

**Build:** `app-debug.apk`, versionName 1.8.2-debug, applicationId `com.tideo.autobrightness.debug`
(installs alongside a release install without touching its data).

```bash
adb install -r app-debug.apk
```

---

## 1. Dimming strength no longer lies (DB-008)

The value was always clamped to 65 in the math; the field kept showing what you typed. Now the
*setpoint* is clamped, so the number on screen is the number in effect.

1. Super Dimming → **Strength Setpoint = 100** → **Apply**.
2. **Pass:** a message says the setpoint was reduced to 65, **and the field itself now reads 65**.
   **Fail:** the field still reads 100, or no message appears.
3. Leave the screen and come back. **Pass:** still 65 (it persisted, and the screen isn't stuck dirty).
4. Set **64** → Apply. **Pass:** stays 64, no message — the clamp only speaks when it actually acts.
5. Set exactly **65** → Apply. **Pass:** stays 65, **no message**. This is the one deliberate
   difference from Tasker, which flashes at exactly 65 for a value it didn't change. Tell me if you'd
   rather have bit-exact parity here.

**Then confirm the fix changed only what's displayed, not what's applied** — this is the reporter's
original measurement:

```bash
adb shell settings get secure reduce_bright_colors_level
```

With strength 65, threshold 1, circadian spread 0, in a dark room: the level should behave exactly as
it did before this change. If dimming got *weaker*, that's a real bug — the clamp was supposed to be
cosmetic-only at the value level.

## 2. Panic gesture: plugged-in-only toggle (DB-009)

New switch on **Live Debug**, under Panic Sensitivity: *"Only when plugged in"*, default **off**.

1. **Default off, on battery:** unplug, screen on, flip the phone upside down and shake.
   **Pass:** panic fires (SOS vibration, brightness to 255, service stops).
2. **Turn the toggle ON, stay unplugged:** repeat the gesture.
   **Pass:** nothing happens. **Fail:** it still fires.
3. **Plug in a USB cable, gesture again.** **Pass:** fires normally.
   (Worth doing right after step 2 without locking the screen — it verifies the toggle takes effect
   immediately rather than at the next screen-off, which is the part I had to wire specially.)
4. **Unplug while the toggle is still ON**, gesture again. **Pass:** nothing happens.
5. **Turn the toggle back OFF while unplugged**, gesture again. **Pass:** fires — again without
   needing to lock the screen first.

## 3. Panic gesture still behaves after a screen-off (DB-009 regression risk)

The accelerometer is now released when the screen is off and re-registered on screen-on. The failure
mode I found in testing was the gesture needing an extra flip-straight-and-back afterwards, so this
is worth a direct check:

1. Screen on, service running, toggle **off**. Lock the screen. Wait ~10 s. Unlock.
2. **Immediately** flip upside down and shake — without first flipping straight and back.
3. **Pass:** panic fires on the first attempt. **Fail:** it takes a second flip to arm.

Also confirm the gesture still *can't* fire with the screen off (it never could — arming requires an
interactive display): lock the screen, shake it upside down in your hand, unlock.
**Pass:** brightness is untouched and the service is still running.

## 4. Battery — the actual point of the change

Before this, the accelerometer ran at ~50 Hz for the whole life of the service, including all night.
There's no clean adb readout for "is the app holding a sensor", so this is a soft check over a normal
day rather than a single command:

- With the service running overnight (screen off), compare Settings → Battery usage for Tideo against
  what you're used to. The expectation is a visible drop in the screen-off portion.
- If you want a harder signal while the screen is on:
  ```bash
  adb shell dumpsys sensorservice | grep -i -A3 tideo
  ```
  With the screen **off** (or with the plugged-only toggle on while unplugged), Tideo should have
  **no active accelerometer connection**. With the screen on and the gesture available, it should.

## Reporting

```bash
adb logcat -d > logcat.txt
adb shell settings get secure reduce_bright_colors_level
adb shell settings get secure reduce_bright_colors_activated
```

Section number, what you expected, what happened. The logcat is the tiebreaker.

## Known limits of this round

- The two-rate idea (slow orientation watch, 50 Hz only during the 10 s shake window) is **not** in
  this build. It would cut the remaining screen-on cost, but the gesture's timing constants are tuned
  in frames at ~50 Hz, so changing the rate silently changes how the gesture feels. Say the word and
  I'll do it as its own change with its own tests.
- The plugged check reads the same power state Android reports to any app; wireless charging counts as
  plugged (`EXTRA_PLUGGED > 0`), matching the upstream Java.
