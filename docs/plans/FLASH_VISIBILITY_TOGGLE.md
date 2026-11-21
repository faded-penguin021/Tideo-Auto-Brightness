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

## How the units run (owner, 2026-11-17)

The plan-runner executes the units from the Docker host, with the owner's phone connected over
wireless adb. Code units (1, 2, 3) build and test in the container as usual. Device work (the
Unit 1 read and Unit 4) goes **only through the e2e harness**, `e2e/run.sh`, under RUNBOOK
playbook 9 and `e2e/README.md`:

- **Connection.** The phone is in `adb tcpip` mode. Either the host's adb server
  (`ADB_SERVER_HOST`, default `host.docker.internal:5037`) or one inside the container
  (`adb connect <phone>:5555`, then `ADB_SERVER_HOST=127.0.0.1`), with
  `TIDEO_E2E_SERIAL=<phone>:5555` set in the environment and never written to a file (the
  harness's identifier rule).
- **No hand-typed `adb shell`.** A write outside `run.sh` is unjournaled (playbook 9, "Avoid"),
  and a read outside it can capture other apps' data into a log. Every device read this plan
  needs is a harness READ template or one added under its unit tests.
- **The owner's part stays the owner's.** Typing `INSTALL` for `e2e/run.sh --install`, keeping
  the phone unlocked with display settings untouched, and switching Tideo's Accessibility service
  on and off in Android's settings. The harness can read `secure/enabled_accessibility_services`,
  but by design never writes it (`tideo_e2e/device.py`). The runner asks the owner for each of
  these and waits; it never works around one.
- **Private output stays private.** Snapshots, JUnit XML and pytest reports go to the private
  store (`XDG_STATE_HOME`), never under `e2e/`. Only the conclusion goes into a commit or a
  ledger row: pass, skip or fail counts, and which form the Accessibility entry takes.

## Units

Each unit ends shippable: STATE Changelog line, `scripts/ladder.sh` green, commit,
`scripts/ladder.sh --guards-only`, push (PROTOCOLS Session discipline 3). Units 1 and 2 touch
`:app` runtime glue, so each runs the **glue-review protocol** (PROTOCOLS). Unit 3 touches only
`e2e/` and the device script. Before editing, run
`python3 scripts/codemap.py impact` on each file named below. No flash feature is registered in
`codemap/features.toml` yet; Unit 2 registers one (`flash`: `AabFlash`, `AabFlashHost`,
`AabToastAccessibilityService`, `ToastDebugSink`, the new store) and runs
`python3 scripts/codemap.py write`.

**Version.** 1.15.3 is tagged, so the first code unit owes a bump (session banner). If Units 1 and
2 ship in one train (recommended), Unit 1 bumps to **1.16.0 / vc34**, since Unit 2 adds a setting
(RUNBOOK playbook 6: minor), and Unit 2 does not bump again. If Unit 1 ships alone, it is
**1.15.4 / vc34**. The bump adds `changelogs/34.txt` and re-checks `MATH_EXPLAINED.md` (DE-039).
This plan changes no `domain/` code. **The light refactor (DE-044…DE-051; its plan is done and
retired) runs earlier in the same train and does not bump
(owner, 2026-11-20; DE-043).** This bump is the train's only one. Its changelog also describes the
refactor's user-visible changes. Its `MATH_EXPLAINED.md` re-check covers the refactor's dimming
readout, chart and `domain/` changes, so it is more than a re-pin.

### Unit 0 — this plan (done in this commit)

This file, DE-040 (the plan) and DE-041 (the owner's decisions), and the STATE Active-work entry
and Changelog lines.

### Unit 1 — make background flashes predictable, and the card truthful (bug fix, playbook 4)

1. **Evidence first (runner, through the harness).** The READ template
   `settings_get_enabled_accessibility_services` already exists, but no harness code calls it:
   preflight snapshots only restorable keys (`SNAPSHOT_KEYS`). So `e2e/tideo_e2e/preflight.py`
   gains one report line saying whether a Tideo entry is listed, and in which form: **long**
   (`<pkg>/<pkg>.app.runtime.AabToastAccessibilityService`), **short**
   (`<pkg>/.app.runtime.AabToastAccessibilityService`) or **absent**. The line never prints the
   setting itself, since it names other apps' services. A device-free unit test under
   `e2e/tests/unit/` covers the three forms, and `e2e/run.sh tests/unit` stays green. Then, with
   the owner having switched the service **on**, the runner runs `e2e/run.sh --preflight`, which
   is read-only. **Short** confirms Finding 2's cause. **Long** means the cause is elsewhere: the
   unit stops after step 2 and reports to the owner.
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
5. **Acceptance:** `scripts/ladder.sh` and `e2e/run.sh tests/unit` green, and the preflight line
   read. Device checks are queued for Unit 4, since the container has no emulator and the phone
   run needs the build from Unit 2.

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

### Unit 3 — the device steps and their e2e scenarios (device-free)

A new `DEVICE_TEST_SCRIPT.md` section, **§19 Flash visibility (#161)**, with steps **62–66**, and
one `e2e/scenarios.toml` row per step, `s19_62` to `s19_66`, written in the **same commit**
(RUNBOOK playbook 5, "Device steps change in two places"; `tests/unit/test_manifest.py` fails
otherwise). The section's `E2E auto:` line names the auto rows.

**The flash trigger.** Context loads cannot be restored (the profile tuple, `s13_44`), so the
scenarios use `s13_44a`'s trigger. Set the debug level to 8 (Context Automation) through the UI
with external control off, then send any control broadcast, and `ControlReceiver.flashDrop` posts
one flash. The effect kinds already exist: `prefs_ui`, `broadcast` and `automation_events`.

**The oracles, all harness READs:**

- **In-app pill:** a node with resource-id `aab_flash` in the uiautomator dump. `AabFlashHost` sits
  inside `TideoRootSurface`'s `testTagsAsResourceId` (DD-018), so the tag is visible.
- **Accessibility overlay:** a `TYPE_ACCESSIBILITY_OVERLAY` window owned by the debug package in
  `dumpsys window windows`. The overlay view has no resource-id (`e2e/README.md` Known limits), but
  its window is listed.
- **Plain toast:** **unverified.** Since Android 11, SystemUI draws a text toast, and whether the
  redacted `dumpsys notification` or `dumpsys window windows` shows it on the owner's phone is not
  known. The scenario's parser accepts only a toast record attributed to the debug package. A
  device-free unit test pins that shape against a sanitised sample the runner captures in Unit 4
  (sanitised text only, into `e2e/evidence/`). Until that sample exists, this half of each row is
  `partial`, with that reason.
- **Absence** is read over a 3 s window, longer than the 2.5 s flash. Each absence check is paired
  in the same test with a positive control under **On** using the same trigger, as `s13_42` pairs
  quiet and control. Without the pair, a broken trigger would pass every absence check.

**Harness changes** (`e2e/tideo_e2e/`, each with unit tests; `test_harness.py` checks every call
against its row):

- A `flash_prefs` effect kind in `effects.py`, covering the private store
  `files/datastore/flash_prefs.preferences_pb`. That path already matches `_PRIVATE_FILE` in
  `device.py`, so no new template is needed. A parser in `state.py` reads it.
- A UI restore routine in `tideo.py` that selects the journaled state on the Live Debug card,
  since "a preference is restorable only through a registered UI routine" (`e2e/README.md`).
- `Target`s for the three options (`flash_visibility_everywhere`, `flash_visibility_inside`,
  `flash_visibility_off`) in the rows' `UiAllowlist`.
- Optional: `global/always_finish_activities` as a restorable `_BIT` in `device.py`'s `SETTINGS`,
  to run step 62's destroyed-activity half. Without it, that half stays `manual`. The stopped half
  is the bug, and it needs no new key.

**Steps** (expected results as in the script):

- **62 On.** Open Live Debug, press Home, trigger a flash. **Expected:** a plain toast, or the
  overlay if the owner has enabled the service, over the launcher. Before Unit 1 this showed
  nothing, because the activity was only stopped. Inside Tideo: the pill. Row `auto`; the toast
  oracle is `partial` as above.
- **63 Off (outside Tideo).** The same two cases. **Expected:** nothing over the launcher; the pill
  inside Tideo. Row `auto`.
- **64 Off (everywhere).** The same two cases. **Expected:** nothing in either place, and the debug
  selector's note is shown. Row `auto`.
- **65 Overlay status.** With the service switched on by the owner, the card reads On. After the
  owner switches it off, the card reads Off on return. The harness reads both the card and the
  setting, and the owner flips the switch. Row `partial`: "the harness never writes
  enabled_accessibility_services".
- **66 The reporter's scenario.** **Off (outside Tideo)**, phone idle for an hour, then context
  switches in another app. **Expected:** no toast. Row `manual`: it needs a context load and an
  hour of real use. The owner runs it, and the reporter on #161 if they will.

**Acceptance:** `e2e/run.sh tests/unit` green (manifest, boundary, harness, the new parsers), and
`scripts/ladder.sh` green. Neither the ladder nor CI runs the e2e unit tests, so the runner runs
them explicitly.

### Unit 4 — the phone run (runner and owner, RUNBOOK playbook 9)

Preconditions, from the owner: the phone unlocked on wireless adb, `TIDEO_E2E_SERIAL` set,
English, and display settings left alone for the run. `TIDEO_E2E_CONFIRM=automation` is enough,
since no row needs `contexts` or `unlock`.

1. `e2e/run.sh tests/unit`, then `e2e/run.sh --preflight`. Anything but exit 0 stops the run.
2. Build the debug APK from the train's head. Install it with `e2e/run.sh --install <apk>`; the
   owner types `INSTALL`.
3. Capture the plain-toast sample for Unit 3's parser. Under On, trigger a flash with Tideo in the
   background, take the READ dumps, and keep only the sanitised toast record. If the phone shows
   no toast record, record that. Steps 62–64's toast halves then stay `partial`, and the owner
   confirms them by eye during the run. If the record's shape differs from what Unit 3's parser
   expects, fix the parser and its unit test, rerun `e2e/run.sh tests/unit`, and commit before
   step 4.
4. `e2e/run.sh tests --ignore=tests/unit -k "s19_" --junitxml=<private store>/junit-<date>.xml`,
   with the service **off**. The owner then switches it **on**, and the runner repeats the
   command, which covers the overlay branch and step 65.
5. `e2e/run.sh --recover`, then `--preflight` again, and compare its record with step 1's field by
   field (playbook 9 step 7). The new `flash_prefs` store must be back at its original state.
6. Owner-only rows: 62's destroyed-activity half (unless the optional key was added) and 66.

**Acceptance:** every `s19_` row passes or skips by a stated rule, the journal is empty, and step
5 finds no unexplained difference. **Record:** a ledger row naming the build (versionName,
versionCode and commit), the pass, skip and fail counts, and the Accessibility-entry form from
Unit 1. Add a STATE Changelog line. A failure is classified per playbook 9; a Tideo defect goes
back through playbook 4 before close-out.

### Unit 5 — close-out

Every durable fact is in a DE row or a STATE Changelog line; this file is deleted; the STATE
Active-work entry is removed. The owner closes #161.

## Out of scope

- Porting the setting to AAB (owner, #161).
- Replacing the plain toast with a notification or any new surface.
- Why the Accessibility service sometimes disables itself on Android 16 / OnePlus 13 (G2R-F50
  note, owner wontfix). Unit 1 only reports it truthfully.
- Other `HardcodedStringCheckTest` gaps beyond D5.
