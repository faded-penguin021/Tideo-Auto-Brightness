# Flash visibility: a three-state setting, and the two bugs under issue #161

## Status

- **Spec only. No code has changed.** The owner asked for this plan on 2026-11-17, from issue
  [#161](https://github.com/faded-penguin021/Tideo-Auto-Brightness/issues/161). That request
  authorised **Unit 0 only**: committing this file, ledger rows DE-040 and DE-041 and the STATE
  entries on the session branch `ccr-a2faaccb-6q3tw1`. Every later unit needs the owner's separate
  go-ahead.
- **The owner's proposal (2026-11-17):** a toggle that turns flashes over other apps on or off,
  and, optionally, flashes shown only inside Tideo by default, unless a design decision, a ledger
  row or the Tasker port already settled that. It did, partly: see "What is already decided".
- **D1–D5 are decided (owner, 2026-11-17; DE-041).** Flashes show over other apps by default, and
  Live Debug offers two separate off states: **Off (outside Tideo)** and **Off (everywhere)**. See
  "Decisions".
- **The AAB-first rule does not apply here (owner, #161, 2026-11-17).** The README sends features
  to AAB first. On #161 the owner wrote that this one will not be ported to AAB first, because
  both AAB designs (a state profile that tracks the foreground app, or a foreground-app poll before
  each Flash) are poor. This is a Tasker-independent feature: RUNBOOK playbook 5, with its
  departure from AAB recorded in STATE and the ledger.
- No unit opens a PR, replies on an issue, tags or releases. The owner relays anything for #161.

## What the reporter and the owner saw

- **Reporter (#161, 2026-11-14 and 11-15).** Every profile switch shows a toast over the app in
  use. Denying the toast app-op over adb did not stop it. The toasts appear with **Global flash**
  off in Live Debug and Tideo's Accessibility service off in Android's settings. Turning the
  service on and off again stopped them "for a time", and after the phone sat idle they came back.
- **Owner (#161, 2026-11-15).** Toasts appear only inside Tideo unless Global flash is on. On the
  owner's phone the service was on in Android's Accessibility settings while Live Debug's card
  read **Off (foreground only)**.

## What the code does today (read at `74021dd`)

`AabFlash.show` (`app/runtime/AabFlash.kt`) is the one channel every flash goes through (D-058).
It picks the first surface it has:

1. The Accessibility overlay, when `AabToastAccessibilityService` is bound (G2R-F50, D-058).
2. The in-app pill, when an `AabFlashHost` is registered (G2R-F88).
3. Otherwise a plain text `Toast` (D-093). Android 11+ blocks only custom-view toasts from the
   background, so a plain text toast still draws over other apps.

Callers: context loads (`ToastContextLoadSink`, G2R-F25), hand-off and High Brightness Mode
(`ToastHandoffSink`, DD-081), the manual-override flash (`AmbientMonitoringService`, G2R-F91), the
ten debug categories (`ToastDebugSink`, D-023), `ControlReceiver`'s dropped-intent flashes
(DB-035), and every in-app confirmation through `Toaster`.

**Finding 1: a stopped activity swallows flashes; a destroyed one lets them out.**
`AabFlashHost` registers when it enters composition and unregisters when it leaves it
(`AabFlashHost.kt`, the `DisposableEffect`). Its KDoc says "cleared when the app is backgrounded",
but leaving the screen only stops the activity. Composition ends when the activity is destroyed.
So:

- While Tideo's activity is alive in the background, every flash goes to an in-app pill nobody can
  see. This is what the owner saw: "only inside Tideo".
- Once Android destroys the activity (idle, memory pressure, a swipe away), no host is registered
  and every flash becomes a plain system toast over other apps. This is what the reporter saw. It
  explains "stopped for a time, came back after idle": the reporter had just opened Tideo, which
  left a live activity behind, and the activity was later reclaimed. Toggling the service is
  probably not what stopped the toasts; reopening Tideo was. This is a reading of the code, not a
  device observation. Unit 1's device check confirms or refutes it.

Neither outcome is a choice anyone made. Whether a background flash appears depends on whether
Android has reclaimed the activity.

**Finding 2: the card can read Off while the service is on.**
`LiveDebugViewModel.isGlobalToastServiceEnabled` compares each entry of
`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` as a string with
`ComponentName(app, AabToastAccessibilityService::class.java).flattenToString()`, which is the long
form `com.tideo.autobrightness/com.tideo.autobrightness.app.runtime.AabToastAccessibilityService`.
Android's own Settings writes the long form, but nothing stops a system app or an OEM settings
screen from writing the short form `com.tideo.autobrightness/.app.runtime.AabToastAccessibilityService`,
which this test misses. That is the likeliest cause; it is **not confirmed**. The owner's
`adb shell settings get secure enabled_accessibility_services` on the phone that read Off settles
it (Unit 1, step 1). The card also never asks whether the service is actually bound
(`AabFlash.isGlobal()`), so a service Android lists but has stopped (the self-disabling noted
under G2R-F50 in `docs/history/STATE_rebuild.md`) reads Enabled.

**Also noticed:** `ToastContextLoadSink` builds its text in code — `Context "…" → profile "…"` —
so the flash the reporter sees most is the one flash that is never translated.
`HardcodedStringCheckTest` does not catch it, because it scans `toast("` and the sink calls
`AabFlash.show`. D5 fixes it in Unit 2.

## What is already decided

- **Tasker parity is system-wide.** AAB's Flash action draws over every app. G2R-F50 recorded it
  as a gap: "Toasts only showed in the foreground; Tasker flashed everywhere". The owner chose an
  opt-in Accessibility overlay to close it (D-058).
- **D-093 chose a plain toast for the background**, so a background flash reaches the user even
  without the overlay, after an external review found custom-view toasts blocked from the
  background on Android 11+.
- **But the UI says the opposite.** The card's off state reads "Off (foreground only)", its help
  text and the service description describe the overlay as the way to see flashes over other apps,
  and D-058 calls the fallback "the foreground toast". The intent as written and the behaviour as
  built disagree. Today's code settles what the app does, not what it should do (AGENTS.md), so D1
  went to the owner.
- **The overlay stays.** The teal Accessibility overlay is an owner-binding keep (the S12.9 review
  rejections, LEDGER.md near D-072).

So defaulting to local flashes is not free: it reverses D-093 and moves away from AAB, which is a
recorded departure, not a cleanup. The owner kept the default (D1 below).

## Decisions (owner, 2026-11-17; DE-041)

**D1. One setting with three states, default On.** Flashes keep showing over other apps by
default, so AAB's behaviour and D-093 stand. Live Debug adds two separate off states:

| State (stored value) | Tideo on screen | Tideo not on screen |
|---|---|---|
| **On** (`EVERYWHERE`, default) | overlay if bound, else the in-app pill | overlay if bound, else a plain toast |
| **Off (outside Tideo)** (`INSIDE_TIDEO`) | overlay if bound, else the in-app pill | nothing |
| **Off (everywhere)** (`OFF`) | nothing | nothing |

Existing installs get On, since no stored value exists yet. The labels follow the owner's
wording. The overlay's "Off (foreground only)" status label is retired, because the overlay's
status no longer describes where flashes appear.

**D2. The user chooses; the state governs every flash.** There are no per-kind exceptions. Context
loads, hand-off and High Brightness Mode, the manual-override flash, the ten debug categories,
`ControlReceiver`'s flashes and the in-app confirmations through `Toaster` (Applied, Copied, the
location messages) all follow it. So **Off (everywhere)** also silences the message a button
press would show. The manual override keeps its heads-up notification (`notifyManualOverride`).
A debug category picked under **Off (everywhere)** would show nothing, so the debug selector shows
one line saying so, without overriding the user's choice.

**D3. Live Debug, in the Global flash card,** retitled for what it holds ("Flashes"). Tideo's
other global switches already live on that screen and apply at once, without Apply/Discard. The
Accessibility overlay stays in the same card as the way to get the teal, tap-to-dismiss surface
instead of a plain toast. The design-tells plan (`DESIGN_TELLS_REDESIGN.md`) also touches Live
Debug's headers and labels; whichever lands second adopts the other's wording.

**D4. Its own Preferences store,** as `ControlPrefsStore` does for external control and force-dark
(D-157). It is not an `AabSettings` field, so it never enters a profile, an export, a Tasker
legacy import or the context baseline (D-170), and the light-refactor plan's `keepGlobalsFrom`
unit is untouched.

**D5. The context-load flash becomes a translatable string** in `strings.xml`:
`flash_context_loaded`, with two arguments (context, profile). `HardcodedStringCheckTest` also
scans `AabFlash.show("`, so the gap cannot reopen. It adds one string to the Chinese backlog
(Owner queue item 1), alongside the strings Units 1 and 2 add.

## Units

Each unit ends shippable: STATE Changelog line, `scripts/ladder.sh` green, commit,
`scripts/ladder.sh --guards-only`, push (RUNBOOK Session discipline 3). Both code units touch
`:app` runtime glue, so each runs the **glue-review protocol** (RUNBOOK). Before editing, run
`python3 scripts/codemap.py impact` on each file named below. No flash feature is registered in
`codemap/features.toml` yet; Unit 2 registers one (`flash`: `AabFlash`, `AabFlashHost`,
`AabToastAccessibilityService`, `ToastDebugSink`, the new store) and runs
`python3 scripts/codemap.py write`.

**Version.** 1.15.3 is tagged, so the first code unit owes a bump (session banner). If Units 1 and
2 ship in one train (recommended), Unit 1 bumps to **1.16.0 / vc34**, since Unit 2 adds a setting
(RUNBOOK playbook 6: minor), and Unit 2 does not bump again. If Unit 1 ships alone, it is
**1.15.4 / vc34**. The bump adds `changelogs/34.txt` and re-checks `MATH_EXPLAINED.md` (DE-039);
no `domain/` change is planned, so that re-check is a re-pin.

### Unit 0 — this plan (done in this commit)

This file, DE-040 (the plan) and DE-041 (the owner's decisions), and the STATE Active-work entry
and Changelog lines.

### Unit 1 — make background flashes predictable, and the card truthful (bug fix, playbook 4)

1. **Evidence first (owner, on device).** On the phone whose card read Off with the service on:
   `adb shell settings get secure enabled_accessibility_services`. If the Tideo entry is the short
   form (`…/.app.runtime.AabToastAccessibilityService`), Finding 2's cause is confirmed. If it is
   the long form, the cause is elsewhere; the unit then stops at step 3 and asks.
2. **`AabFlashHost` follows the activity's lifecycle, not composition.** It registers its presenter
   on `ON_START` and unregisters it on `ON_STOP`, through `LocalLifecycleOwner`, with no new
   dependency. DD-034 still holds: a host clears only its own registration. Result: while Tideo is
   not visible, the flash always takes the overlay or plain-toast path. That is today's behaviour
   once the activity is destroyed, now every time, so this unit makes D-093 consistent without
   changing what is decided. The toggle arrives in Unit 2.
3. **The card's status reads the component, not a string.** Each entry is parsed with
   `ComponentName.unflattenFromString` and compared with the service's `ComponentName`, so the
   short and long forms both match. The card shows three states: **On**, **Listed but not
   running** (listed in settings, `AabFlash.isGlobal()` false), and **Off**. The overlay's
   "Off (foreground only)" becomes a plain "Off" (D1), plus one new string for "Listed but not
   running". Both go on Owner queue item 1's list.
4. **Tests (Robolectric, failing first):**
   - `AabFlashTest`: after the host's lifecycle reaches `ON_STOP`, `AabFlash.show` posts a plain
     toast (ShadowToast) and the host's message stays null; after `ON_START`, the pill shows it.
   - A pure parser test for the status: long form, short form, a different package, an empty
     setting, and the `.debug` application ID.
   - `SettingsScreensTest` (or the Live Debug UI test that owns `global_flash_card`): the three
     status labels render.
5. **Acceptance:** `scripts/ladder.sh` green. Device checks are queued for Unit 3; this container
   has no emulator.

### Unit 2 — the three-state setting (feature, playbook 5)

1. **Store (D4).** A `FlashPrefsStore` on a new `flash_prefs` Preferences DataStore, holding one
   string key `flash_visibility` that is read into an enum
   `FlashVisibility { EVERYWHERE, INSIDE_TIDEO, OFF }`. A missing or unrecognised value reads as
   `EVERYWHERE` (D1). Registered in `AppModule` beside `ControlPrefsStore`.
2. **Channel.** `AabFlash` gains a `@Volatile visibility: FlashVisibility?`, set by a collector of
   the store that `AppModule` starts once per process. `show` keeps its `cancel()` first (G2R-F51),
   then applies D1's table:
   - `OFF`: show nothing.
   - `INSIDE_TIDEO`: if a foreground presenter is registered (Tideo on screen, after Unit 1), the
     overlay if bound, else the pill; otherwise nothing.
   - `EVERYWHERE`: as today. The overlay if bound, else the pill if Tideo is on screen, else a
     plain toast.
   - `null` (the store's first value has not arrived): behave as `INSIDE_TIDEO`. A user who chose
     either off state never sees a cold-start toast over another app, and an On user can lose a
     background flash only in the process's first milliseconds. Under `OFF`, an in-app flash can
     slip through in that window only if the activity starts before the store loads. That is
     accepted, since the store loads within a frame of process start.
   - A change to a stricter state calls `AabFlash.cancel()`, so a flash already on screen goes.
   The gate lives in `AabFlash`, not in each sink, so D2's "every flash" is one rule and the sinks
   are not touched. That includes `Toaster`, which already calls `AabFlash.show`.
3. **UI (D3).** The Live Debug card, retitled "Flashes" (test tag `flash_visibility_card`), opens
   with a three-option single-choice row (`SingleChoiceSegmentedButtonRow`, or the radio list the
   debug selector uses if the design rules prefer it), with test tags `flash_visibility_everywhere`,
   `flash_visibility_inside`, `flash_visibility_off`, and these labels:
   - **On**: "Flashes show over the app you are using."
   - **Off (outside Tideo)**: "Flashes show only while Tideo is open."
   - **Off (everywhere)**: "No flashes, including confirmations inside Tideo."

   The help line under the row changes with the state. The overlay status and the
   Accessibility-settings button follow, and the button is disabled unless the state is On (the
   overlay only matters for flashes over other apps). While the state is **Off (everywhere)** and
   the debug category is not Off, the debug selector shows "Flashes are off, so debug messages
   will not show." The service's Accessibility description (`global_toasts_a11y_description`) is
   reworded to say the setting lives in Live Debug. Every label is a `strings.xml` resource (D-131).
4. **D5.** `ToastContextLoadSink` formats `flash_context_loaded` through
   `notificationLanguageContext`, as `ToastHandoffSink` does. `HardcodedStringCheckTest` adds
   `AabFlash.show("` to the shapes it scans.
5. **Tests (Robolectric, failing first):**
   - `AabFlashTest`: a truth table of visibility (the three states and `null`) × Tideo on screen ×
     overlay bound, checking which surface receives the text, or none (ShadowToast for the plain
     toast). Also: a change to a stricter state cancels a flash already showing.
   - `FlashPrefsStore`: each state round-trips, and missing and unknown values read as `EVERYWHERE`.
   - A Live Debug UI test: each option writes its state, the overlay button is enabled only under
     On, and the debug note appears only under **Off (everywhere)** with a category selected.
   - `ToastContextLoadSink`: the flash text comes from the resource, with both arguments.
   - `RuntimeDebugTest` and `ControlReceiverTest` pass unchanged, which shows the sinks' gating did
     not move.
6. **Record:** the STATE departure line ("flashes can be limited to inside Tideo or turned off;
   AAB has no such switch; owner waived AAB-first on #161"), a DE row for what shipped, the
   `changelogs/34.txt` entry, the new strings on Owner queue item 1's list, and
   `codemap/features.toml`.
7. **Acceptance:** `scripts/ladder.sh` green.

### Unit 3 — device checks (owner; the reporter on #161 if they will)

Added to `docs/rebuild/DEVICE_TEST_SCRIPT.md` and its row in `e2e/scenarios.toml` in the **same
commit** (RUNBOOK playbook 5, "Device steps change in two places"); Unit 2 writes both.

1. **On**, overlay off. Open Tideo, press Home, trigger a context switch (for example a battery
   or app rule). **Expected:** a plain toast over the launcher. Repeat with Developer options →
   "Don't keep activities" on, so the activity is destroyed when it leaves the screen.
   **Expected:** the same toast both ways. Before Unit 1 the first case showed nothing, because
   the activity was only stopped. Turn the overlay on and repeat. **Expected:** the teal overlay
   instead of the plain toast.
2. **Off (outside Tideo)**, overlay off and then on. Same triggers with Tideo in the background.
   **Expected:** nothing over other apps. With Tideo open, trigger the same switch, or press Apply
   on any settings screen. **Expected:** the teal pill shows.
3. **Off (everywhere)**. Same triggers in the background, then Apply inside Tideo. **Expected:**
   nothing in either place. Pick a debug category. **Expected:** the note that debug messages will
   not show.
4. Live Debug's overlay status on the phone that read Off with the service on. **Expected:** On.
   Disable the service in Android's settings. **Expected:** Off on return.
5. The reporter's scenario on #161: **Off (outside Tideo)**, phone idle for an hour, then profile
   switches in another app. **Expected:** no toast.

### Unit 4 — close-out

Every durable fact is in a DE row or a STATE Changelog line; this file is deleted; the STATE
Active-work entry is removed. The owner closes #161.

## Out of scope

- Porting the setting to AAB (owner, #161).
- Replacing the plain toast with a notification or any new surface.
- Why the Accessibility service sometimes disables itself on Android 16 / OnePlus 13 (G2R-F50
  note, owner wontfix). Unit 1 only reports it truthfully.
- Other `HardcodedStringCheckTest` gaps beyond D5.
