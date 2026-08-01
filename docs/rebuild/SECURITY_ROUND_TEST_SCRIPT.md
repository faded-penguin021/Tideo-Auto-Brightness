# Device test script — 2026-07-31 security round (DA-043/DA-044 + DB-001…DB-007)

Covers **only what this round changed**, and only the parts a device can answer that JVM/Robolectric
cannot. It is not a regression pass: nothing here re-checks brightness curves, profiles or context
rules, which are untouched and covered by the existing suites.

**Build:** `app-debug.apk` (versionName 1.8.2-debug, applicationId
`com.tideo.autobrightness.debug` — installs alongside a release install without touching its data).

```bash
adb install -r app-debug.apk
# External control is OFF by default; several sections below need it ON:
#   Tools → Automation control → "Allow external control"
```

Package used below: `com.tideo.autobrightness.debug`; receiver component
`com.tideo.autobrightness.debug/com.tideo.autobrightness.app.control.ControlReceiver`.

---

## 1. Control flood is bounded (DA-043)

**What changed:** the receiver admitted one command at a time but released the slot as soon as
routing finished, so a sequential flood moved the backlog into the pipeline queue. That queue is now
coalescing + capped.

```bash
# Service ON, then flood REAPPLY as fast as the shell will send it.
adb shell am broadcast -a com.tideo.autobrightness.control.SERVICE_ON \
  -n com.tideo.autobrightness.debug/com.tideo.autobrightness.app.control.ControlReceiver
for i in $(seq 1 300); do
  adb shell am broadcast -a com.tideo.autobrightness.control.REAPPLY \
    -n com.tideo.autobrightness.debug/com.tideo.autobrightness.app.control.ControlReceiver >/dev/null
done
```

**Pass:** the UI stays responsive; brightness settles once and does not visibly "chase" for a long
time after the loop ends; no ANR. **Fail:** a long tail of re-applies continuing well after the loop,
or a frozen UI.

**Ordering is not sacrificed to the bound** — this is the case that would break if coalescing were
too aggressive:

```bash
P=com.tideo.autobrightness.control; C=com.tideo.autobrightness.debug/com.tideo.autobrightness.app.control.ControlReceiver
adb shell am broadcast -a $P.PAUSE -n $C; adb shell am broadcast -a $P.RESUME -n $C; adb shell am broadcast -a $P.PAUSE -n $C
```

**Pass:** ends **paused** (notification shows paused). **Fail:** ends running.

Unknown verbs must be refused without disturbing a real one sent beside them:

```bash
adb shell am broadcast -a com.tideo.autobrightness.control.NOT_A_VERB -n $C
adb shell am broadcast -a com.tideo.autobrightness.control.PAUSE -n $C
```

**Pass:** the PAUSE takes effect.

## 2. Extra Dim never sticks at a stale level (DB-001) — **ELEVATED only**

**What changed:** if the level write failed while dimming was engaged, the old (usually *stronger*)
level stayed on screen and the log still said `ON`.

Needs `WRITE_SECURE_SETTINGS`. With super dimming enabled and the room dark enough to engage:

```bash
adb shell settings get secure reduce_bright_colors_activated   # expect 1
adb shell settings get secure reduce_bright_colors_level       # note the value
# Revoke mid-flight to force the write to fail:
adb shell pm revoke com.tideo.autobrightness.debug android.permission.WRITE_SECURE_SETTINGS
# Now brighten the room (or raise min brightness in Settings) so a WEAKER level is requested.
adb shell settings get secure reduce_bright_colors_activated   # expect 0
```

**Pass:** Extra Dim ends **off** rather than stuck at the earlier stronger level; with debug category
5 on, the flash reads `FAILED … Extra Dim cleared`, never `ON`. **Fail:** the screen stays dark at
the old level, or a flash claims `ON <new level>`.

Restore afterwards: `adb shell pm grant com.tideo.autobrightness.debug android.permission.WRITE_SECURE_SETTINGS`

## 3. Backup restore drops runtime state (DB-002)

**What changed:** `serviceEnabled` and `contextOverride` travelled inside the backed-up settings
file. They are now reset when a restore lands.

```bash
# With the service RUNNING and a profile manually loaded (so contextOverride is latched):
adb shell bmgr backupnow com.tideo.autobrightness.debug
adb uninstall com.tideo.autobrightness.debug
adb install -r app-debug.apk
adb shell bmgr restore <token>        # `adb shell bmgr list sets` to find the token
```

**Pass, all three:** brightness configuration and saved profiles are back; the service is **off**;
the Profiles screen shows context automation active (no manual lock banner). **Fail:** the service
auto-starts, or the profile lock is still latched.

> The most important case is the *default* one: if you never toggled the service off before backing
> up, the key is absent from the file entirely — that is exactly the case the first version of this
> fix would have missed.

## 4. Privileged grant paths (DB-005)

**What changed:** `pm grant` output no longer counts as failure; the Shizuku permission prompt has a
timeout; concurrent grants are refused rather than racing.

1. **Grant reports success:** with Shizuku running, run the in-app grant (Tools → Privileges →
   Grant via Shizuku). **Pass:** the tier flips to ELEVATED and the UI says granted. Confirm
   independently: `adb shell dumpsys package com.tideo.autobrightness.debug | grep WRITE_SECURE_SETTINGS`.
   (Before this fix, a grant that printed any output reported failure while actually succeeding —
   so the interesting signal is *UI success agreeing with dumpsys*.)
2. **Prompt dismissal terminates:** trigger the grant, then swipe the Shizuku dialog away without
   answering. **Pass:** the UI returns an error within ~2 minutes and the button works again.
   **Fail:** it hangs forever.
3. **Double-tap:** tap the grant button twice quickly. **Pass:** the second reports "a grant is
   already in progress"; no duplicate prompts.

## 5. SAF import cannot freeze the UI (DA-044)

**What changed:** provider I/O moved off the UI dispatcher and gained a 20 s bound.

Best evidence needs a slow provider — a cloud storage app with the network disabled works:

1. Profiles → Load → pick a file from a cloud provider with Wi-Fi/data off mid-pick.
2. **Pass:** the app stays interactive (you can scroll/press Back) and eventually shows an import
   error. **Fail:** the UI freezes until the provider gives up.
3. Also confirm the happy path still works: export a profile to Files, then import it back.

## 6. Unknown service action does not start the runtime (DB-005)

```bash
adb shell am start-foreground-service -a com.tideo.autobrightness.runtime.action.NONSENSE \
  -n com.tideo.autobrightness.debug/com.tideo.autobrightness.app.runtime.AmbientMonitoringService
```

**Pass:** no persistent notification appears and the service does not stay up
(`adb shell dumpsys activity services com.tideo.autobrightness.debug` shows none running).
**Fail:** the runtime starts.

## 7. Geo-IP cancellation (DB-006) — optional, opt-in

Needs the geo-IP fallback consent ON and location otherwise unavailable.

Open the screen that triggers a location refresh, then leave it immediately. **Pass:** no stall on
returning, no delayed toast ~30–60 s later. This is a weak device signal by nature — the mechanism
itself is pinned by `BlockingReadCancellationTest`; a device check only looks for the symptom.

---

## Reporting

For anything that fails, capture:

```bash
adb logcat -d > logcat.txt        # right after the failure
adb shell dumpsys activity services com.tideo.autobrightness.debug
adb shell settings get secure reduce_bright_colors_activated
adb shell settings get secure reduce_bright_colors_level
```

Section number + what you expected + what happened is enough; the logcat is the tiebreaker.
