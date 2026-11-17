# Flashes over other apps: a toggle, and the two bugs under issue #161

## Status

- **Spec only. No code has changed.** The owner asked for this plan on 2026-11-17, from issue
  [#161](https://github.com/faded-penguin021/Tideo-Auto-Brightness/issues/161). That request
  authorised **Unit 0 only**: committing this file, ledger row DE-040 and the STATE entries on the
  session branch `ccr-a2faaccb-6q3tw1`. Every later unit needs the owner's separate go-ahead.
- **The owner's proposal (2026-11-17):** a toggle that turns flashes over other apps on or off,
  and, optionally, flashes shown only inside Tideo by default, unless a design decision, a ledger
  row or the Tasker port already settled that. It did, partly: see "What is already decided". D1
  asks the owner to choose.
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
`AabFlash.show`. D5 asks whether to fix it here.

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
  goes to the owner.
- **The overlay stays.** The teal Accessibility overlay is an owner-binding keep (the S12.9 review
  rejections, LEDGER.md near D-072).

So defaulting to local flashes is not free: it reverses D-093 and moves away from AAB, which is a
recorded departure, not a cleanup. It is still the owner's call, and D1 lays it out.

## Decisions for the owner

**D1. What happens to a flash while Tideo is not on screen, by default?**

- (a) **Shown over other apps (the default), and the new toggle turns that off.** This keeps AAB's
  behaviour and D-093, and matches what most users already see once Android reclaims the activity.
  The card's "Off (foreground only)" label is corrected to what it means.
- (b) **Shown only inside Tideo (the default), and the toggle turns flashes over other apps on.**
  This matches the card's current label and the reporter's expectation, and stops the toast for
  every new and existing user who does nothing. It departs from AAB (a DE row), reverses D-093,
  and users who rely on the profile-switch flash lose it until they find the toggle.

  Recommendation **(a)**: parity is binding unless the owner departs from it, and a user who never
  opened Live Debug should not lose a flash they see today. If the owner prefers (b), Unit 2 is the
  same code with the other default and one more ledger sentence. Either way, existing installs get
  the default, since no stored value exists yet.

**D2. What does the toggle silence?**

- (a) **Every flash while Tideo is not visible:** context loads, hand-off and High Brightness Mode,
  the manual-override flash, the debug categories and the control-intent flashes. Flashes while
  Tideo is on screen always show.
- (b) Only the context and profile-load flash.
- (c) Everything in (a) except the debug categories, since a user picks those on purpose.

  Recommendation **(a)**: one rule a user can predict. A user debugging in other apps leaves the
  toggle on. The manual-override flash duplicates the heads-up notification
  (`notifyManualOverride`), so nothing is lost when it is silenced.

**D3. Where does the toggle live?**

- (a) **Live Debug's Global flash card, renamed for what it now holds** (for example, "Flashes over
  other apps"). Tideo's other global switches (debug category, panic sensitivity, panic only while
  charging) already live on that screen and apply at once, without Apply/Discard.
- (b) Misc → Notifications. Easier to find, but Misc is a profile draft screen with Apply and
  Discard, and this setting belongs to no profile.

  Recommendation **(a)**, with the Accessibility overlay kept in the same card as the way to get
  the teal, tap-to-dismiss surface instead of a plain toast. The design-tells plan
  (`DESIGN_TELLS_REDESIGN.md`) also touches Live Debug's headers and labels; whichever lands
  second adopts the other's wording.

**D4. Where is the setting stored?**

Recommendation, not a fork: a new key in its own Preferences store, as `ControlPrefsStore` does for
external control and force-dark (D-157). It is not an `AabSettings` field, so it never enters a
profile, an export, a Tasker legacy import or the context baseline (D-170), and the light-refactor
plan's `keepGlobalsFrom` unit is untouched. The owner only needs to object if they want it in
profiles.

**D5. Translate the context-load flash in the same change?**

- (a) **Yes, in Unit 2:** a `flash_context_loaded` string with two arguments, and the ratchet
  extended to `AabFlash.show("`. It adds one string to the Chinese backlog (Owner queue item 1).
- (b) No, leave it for its own unit.

  Recommendation **(a)**: it is the flash this issue is about, and the change is a few lines.

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

This file, DE-040, and the STATE Active-work entry and Changelog line.

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
   running** (listed in settings, `AabFlash.isGlobal()` false), and **Off**. Two new strings; "Off
   (foreground only)" is reworded per D1. That adds strings to Owner queue item 1.
4. **Tests (Robolectric, failing first):**
   - `AabFlashTest`: after the host's lifecycle reaches `ON_STOP`, `AabFlash.show` posts a plain
     toast (ShadowToast) and the host's message stays null; after `ON_START`, the pill shows it.
   - A pure parser test for the status: long form, short form, a different package, an empty
     setting, and the `.debug` application ID.
   - `SettingsScreensTest` (or the Live Debug UI test that owns `global_flash_card`): the three
     status labels render.
5. **Acceptance:** `scripts/ladder.sh` green. Device checks are queued for Unit 3; this container
   has no emulator.

### Unit 2 — the toggle (feature, playbook 5)

1. **Store.** A `FlashPrefsStore` with one boolean, `flashes_over_other_apps`, default per D1, on a
   new `flash_prefs` Preferences DataStore (D4). Registered in `AppModule` beside
   `ControlPrefsStore`.
2. **Channel.** `AabFlash` gains a `@Volatile` flag, set by a collector of the store started once
   per process in `AppModule`. In `show`:
   - Tideo visible (a foreground presenter is registered): as today. The overlay if bound, else
     the pill.
   - Tideo not visible and the flag on: as today. The overlay if bound, else a plain toast.
   - Tideo not visible and the flag off: cancel any previous flash and show nothing.
   - Before the store's first value arrives: treat the flag as **off** for background flashes, so
     a user who turned it off never sees a cold-start toast. Under D1 (a) this can drop a
     background flash in the process's first milliseconds, which is the cheaper error.
   - Turning the flag off calls `AabFlash.cancel()`, so a toast already on screen goes.
   Silencing happens in `AabFlash`, not in each sink, so D2 (a) is one rule. Under D2 (b) or (c)
   the sinks pass a flash kind to `show` instead; the plan does not pre-build that.
3. **UI (D3).** The Live Debug card becomes the toggle (a `SwitchRow`, test tag
   `switch_flashesOverOtherApps`), with the overlay status and the Accessibility-settings button
   beneath it, disabled while the toggle is off. The strings are new: the toggle label, its help
   ("Profile switches and other messages from Tideo appear over the app you are using. Inside
   Tideo they always show."), and a retitled card. The service's Accessibility description
   (`global_toasts_a11y_description`) is reworded to match.
4. **D5 (a), if chosen:** `flash_context_loaded`, and `HardcodedStringCheckTest` also scans
   `AabFlash.show("`.
5. **Tests:**
   - `AabFlashTest`, a truth table of visible × flag × overlay bound × flag unknown: which surface
     receives the text, or none (ShadowToast for the plain toast).
   - `FlashPrefsStore` round-trips, and its default matches D1.
   - A Live Debug UI test: the switch writes the store, and the overlay button is disabled while
     it is off.
   - `RuntimeDebugTest` and `ControlReceiverTest` still pass unchanged, which shows the sinks were
     not touched.
6. **Record:** the STATE departure line ("flashes over other apps can be turned off; AAB has no
   such switch, owner waived AAB-first on #161"), a DE row stating D1–D5 as decided, the
   `changelogs/34.txt` entry, and `codemap/features.toml`.
7. **Acceptance:** `scripts/ladder.sh` green.

### Unit 3 — device checks (owner; the reporter on #161 if they will)

Added to `docs/rebuild/DEVICE_TEST_SCRIPT.md` and its row in `e2e/scenarios.toml` in the **same
commit** (RUNBOOK playbook 5, "Device steps change in two places"); Unit 2 writes both.

1. Toggle **on**, overlay off. Open Tideo, press Home, trigger a context switch (for example a
   battery or app rule). **Expected:** a plain toast over the launcher. Repeat with Developer
   options → "Don't keep activities" on, so the activity is destroyed when it leaves the screen.
   **Expected:** the same toast both ways. Before Unit 1 the first case showed nothing, because
   the activity was only stopped.
2. Toggle **off**, overlay off and then on. Same triggers. **Expected:** nothing over other apps.
   Inside Tideo the teal pill (or overlay) still shows the flash.
3. Live Debug's status on the phone that read Off with the service on. **Expected:** On.
   Disable the service in Android's settings. **Expected:** Off on return.
4. The reporter's scenario on #161: toggle off, phone idle for an hour, then profile switches in
   another app. **Expected:** no toast.

### Unit 4 — close-out

Every durable fact is in a DE row or a STATE Changelog line; this file is deleted; the STATE
Active-work entry is removed. The owner closes #161.

## Out of scope

- Porting the toggle to AAB (owner, #161).
- Replacing the plain toast with a notification or any new surface.
- Why the Accessibility service sometimes disables itself on Android 16 / OnePlus 13 (G2R-F50
  note, owner wontfix). Unit 1 only reports it truthfully.
- Other `HardcodedStringCheckTest` gaps beyond D5.
