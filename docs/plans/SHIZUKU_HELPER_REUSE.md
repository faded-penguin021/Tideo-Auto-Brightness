# Shizuku helper reuse: no 4 s bind hang on back-to-back calls

## Status

- The owner asked for this plan on 2026-10-26, docs only, on the session branch
  `claude/shizuku-reuse` (from `main` after #154). Every unit needs the owner's separate go-ahead.
- No unit opens a PR, replies on an issue, tags or releases.
- **The fix is the owner's choice (2026-10-26): keep the helper bound.** Astra (`gpt-6-astra -e
  low`) first proposed a 3 s idle window, estimated at 80–120 lines. The owner's constraint is
  efficient with no unnecessary complexity, and a held binding meets it with less code.
- **Sol reviewed the plan (2026-10-26, owner's request).** Its findings are folded in: the
  cause is in the client library, the binding is released on any failure, callbacks never touch
  it, and the device check runs on the debug install. Unit 1 runs on the owner's go-ahead of the
  same day.

## The bug (owner report and device capture, 2026-10-26)

A context rule loads the app-default Outdoors profile (Night Light off) while Google Photos is
open. The baseline has Night Light on, **Follow circadian scaling** on, a 4082 K setpoint and the
extended range on. Going back to the previous app:

- **Release 1.15.1 vc31:** Night Light stays off for about 6 s, comes on at 4082 K, and moves to
  7308 K (the circadian day value at midday) about 5 s later.
- **Debug 1.15.1-debug vc31:** Night Light comes on at 7308 K about 1 s after leaving Photos.

## Cause (measured 2026-10-26)

Measured on the owner's OnePlus 13 (Android 16, Shizuku API 13.1.5), polling the Night Light
keys and the `:aab_shell` helper process every ~0.1 s while also capturing logcat:

| t (s) | Release, on return from Photos |
|---|---|
| 41.27 | Call 1, the anchor read (`acquireAnchorLocked` → `readDeviceKelvin` → `bridge.readKelvin`), starts a helper, answers, and unbinds with `remove = true`. The helper is gone by 41.35 |
| 41.46 | Call 2, the 7308 K write (`writeKelvinLocked` → `bridge.setKelvin`), starts a new helper; Shizuku logs `send binder`, but `onServiceConnected` never fires |
| 45.29 | `BIND_TIMEOUT_MS` (4 s) unbinds it. The route falls back to the device clamp, 4082 |
| 45.54 | Night Light on at 4082 (DD-090 order, "a failed write still switches on") |
| 50.7 | DD-064's early tick makes one Shizuku call, which connects: 7308 |

- **The trigger** is a bind issued about 150 ms after a `remove = true` unbind of the same
  user service.
- **Where the connection is lost (client library, read from the 13.1.5 bytecode; Sol,
  2026-10-26).** A `remove = true` unbind only asks the server to remove the helper; the client
  keeps its cached `ShizukuServiceConnection` (only `remove = false` clears it). Call 2's bind
  joins that cached entry, and when the old helper dies, its `died()` clears every listener on
  it, call 2's included, and drops the entry. Call 2's `onServiceConnected` is never delivered.
- **Why debug differs.** Only the release install has stored the DC-057 verdict that this build
  ignores the Kelvin key. The debug install's `display_prefs` holds only the prior and anchor
  keys. Without the verdict, `readDeviceKelvin` reads the key, so debug makes one Shizuku call
  on return, not two. Any install that learns the verdict will behave like release. DD-093's
  passing check ran without the verdict.

## The fix

`ShizukuShell` binds once and keeps the binding. It stops unbinding with `remove = true` at the
end of every call (DD-077's comment at `ShizukuShell.kt:21`). With no teardown, there is no
rebind to race.

- **Held binder.** The first call binds as today and keeps the connection and binder. Later
  calls use the binder directly while it answers `pingBinder`, so they wait for no bind and no
  main-looper callback (DC-058). The operation still runs on a worker thread, so a timeout
  still returns on time.
- **Release on any failure.** A dead binder (Shizuku restart, helper killed), an operation that
  throws, or a call that times out releases the binding with `remove = true`, and the next call
  binds again. Releasing a wedged helper keeps transactions from overlapping (the `serial` Mutex
  alone cannot stop a timed-out worker thread). Nothing is retried within a call.
- **Only the call touches the binding.** `onServiceDisconnected` stays empty: `pingBinder`
  already catches a dead helper, and no callback can clear or replace a binding. A connect that
  lands after its timeout resumes a cancelled continuation and is ignored.
- **Bind timeout unchanged.** A bind that never connects is still unbound with `remove = true`
  (D-145), and the call returns null for the existing fallback.
- **`daemon(false)`.** The helper dies with Tideo's process, so it never outlives the app
  (Shizuku destroys a non-daemon service when its last client binder dies).

**Accepted residual.** The first call after a helper death can still bind into the dying cached
entry and time out once (the same race, now only after a death). The next call binds cleanly.

**Cost, accepted by the owner.** One idle shell-UID helper while Tideo's process lives. As a
proxy, aShell's resident Shizuku helper on the owner's phone measured 55 MB PSS, 54 MB of it
swapped out, on 2026-10-26.

**What it does not change:** the caller APIs, the anchor read (it is persisted and restores
the device Kelvin when leaving circadian, DC-056), the DD-048 clamp fallback, the DD-090 order,
DC-058's main-thread skip, the timeouts, and `ShizukuGrantGateway` (a separate identity, used
once per grant).

**Accepted limit, documented, not fixed:** the helper stays bound while Tideo's process lives,
including while the service is stopped and only the UI is open.

**Relation to the faster-Shizuku plan.** `PRIVILEGED_FAST_PATHS.md` Unit 6 plans a resident
helper with an idle unbind and teardown release, gated on ColorOS 17. This fix delivers
residency now. Unit 6 keeps whatever of its idle and teardown rules still earns its place and
does not build a second session.

**Options not taken (2026-10-26):**
- Skip the Shizuku read. It would break the anchor restore, and other back-to-back pairs
  (Wi-Fi SSID, force-dark, the hand-off loop) would stay exposed.
- Retry on timeout. The user still sees the stall, and the retry is itself a bind shortly after
  a removal.
- A fixed delay before rebinding. Adds latency, and nobody has measured a safe gap.
- A 3 s idle window (Astra). It fixes the observed pair, but costs a timer, stale-callback
  handling, and a residual race right after each idle removal.

## Execution rules

- **Units.** One unit at a time, following playbook 4 and session discipline. Each ends with a
  STATE Changelog line, a green ladder, a commit, `--guards-only` and a push.
- **Reviews.** A Sol review beside the Unit 1 writes and before its commit. The glue review is
  mandatory, since this is `:platform` glue. A rule review if a comment budget is raised.
- **Size gate.** Unit 1 is drafted, measured, tested and reviewed before it is committed, and
  its measured production diff goes to the owner with the result. A held binding should be a
  few dozen lines. If it grows well past that, stop and report before committing.
- **Version.** 1.15.1 / vc31 is tagged, so Unit 1 is this train's one bump: a patch, 1.15.2 /
  vc32, with `fastlane/metadata/android/en-US/changelogs/32.txt` (500-character cap).
- **Records.** Code cites DD rows, never this plan, and ledger rows never cite its path.

## Units (sequential)

**0. Commit this plan (docs only).** This file, its STATE checklist, a Changelog line, and a
pointer in the faster-Shizuku plan's Unit 6.

**1. The fix, its tests and its records (one commit).**
- **Test first.** Shizuku's binder cannot run under Robolectric (D-145). Put the bind behind a
  small seam (usable, bind, unbind) and drive it with a fake in a JVM test. The fake behaves
  like 13.1.5: a bind issued after a `remove = true` unbind never connects.
  - two back-to-back calls both answer, on one bind (must fail without the fix);
  - a dead binder makes the next call bind again;
  - a bind timeout unbinds with `remove = true` and returns null.

  The last two guard behaviour the old code also had. If the seam costs more than the fix,
  report it before cutting tests.
- **Fix.** As above, in `ShizukuShell.kt`. Replace the DD-077 comment at `:21` with the new
  row's one-line pointer.
- **Comment budget.** No pre-approval. Any `platform` raise is a rule change and needs a rule
  review.
- **Ledger.** New row **DD-094 [cited]**: the cause measured above and the held binding.
  It reopens DD-077's per-call `remove = true` and DC-057's per-operation binding. Append a
  pointer line to each of those two rows.
- **Device steps.** Extend step 38b in `docs/rebuild/DEVICE_TEST_SCRIPT.md` with a case on an
  install holding the key-ignoring verdict: a context switch back from a Night-Light-off profile
  brings Night Light on at the circadian value about 1 s after the switch, with no 4 s pause and
  no 4082 step. Add the preparation that gives the **debug** install the verdict with the app's
  own probe, so the case runs before a release (owner, 2026-10-26): force-stop the debug app;
  with Shizuku running and the service off, open Privileged Display, turn the extended range
  off, Apply an in-range Kelvin that differs from the device's, wait 2 s, Apply a second
  different one, wait 2 s; `adb shell run-as com.tideo.autobrightness.debug cat
  files/datastore/display_prefs.preferences_pb | grep -ac night_light_key_not_honoured` prints 1.
  Then restore the extended range and the circadian setup. Refresh the `s11_38b` row in
  `e2e/scenarios.toml` in the same commit.
- **Version.** 1.15.2 / vc32 and `32.txt`.
- **Gates.** `scripts/ladder.sh` green, the Sol review, then the glue review.
- **Commit body.** Says what ran, and that no device check was done.

**2. Owner device check, and close-out.**
- Test on the **debug** install after step 38b's preparation gives it the verdict; without it,
  debug makes one Shizuku call on return and never takes the two-call path. The release install
  is not needed.
- With an adb poll running, do about 5 Outdoors → baseline context switches. **Expected:** one
  `:aab_shell` PID stays up across all of them; the key reads 7308 before
  `night_display_activated` goes to 1; no 4 s gap.
- Then leave the circadian profile once: the device Kelvin is restored (DC-056).
- Restart Shizuku once: the next switch binds a new helper and still lands 7308 first.
- Force-stop Tideo: the helper exits with it (`daemon(false)`).
- **Records.** A DD row recording the result; a failure is recorded and the fix is not
  shipped untested. Delete this file and its STATE checklist; the durable content lives in the
  ledger rows and Changelog lines.
