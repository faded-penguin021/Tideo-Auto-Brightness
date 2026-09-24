# STATE — project state & session memory

> **Length guard (DA-004).** Thresholds are in `amh.conf`; the compression rules, and the rule for
> what may sit in `Current state` at all, are `docs/RUNBOOK.md` → **Working-memory compression**,
> and they bind whether or not you follow this pointer. Prose-only — no guard judges it.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

Harness AMH 14.1.0 with its one hand step applied (DC-029…DC-036, DC-051); upstream manifest
scripts are immutable; the live ledger is `LEDGER_C.md`. **Release standing is NOT recorded
here:** the session banner computes it (`scripts/session-facts.sh`, DC-030), settled by hand with
`git ls-remote --tags --refs origin 'refs/tags/v*'`.

This branch carries the #126/#127 override-attribution work (DC-002…DC-028), the harness units,
the runtime rot audit (DC-042…DC-046), the Night Light work (DC-053…DC-058), the proximity-damp
parity restore (DC-064) and light-stall R1–R6 (DC-063, DC-065…DC-069). Device rounds on 1.10.0-debug vc24 are closed, with the 0–4095 conversion path frozen as
built, and a later build owes its own run (DC-011…DC-013, DC-025…DC-028, DB-083;
`DEVICE_TEST_SCRIPT.md` §2); no round script is alive (RUNBOOK §6, DB-010), the force-stop
investigation stays closed (DB-051…DB-060), and Scorecard.dev is a run-once local input.

## Active work

- **Real-device E2E suite** — `docs/plans/DEVICE_E2E_PLAN.md` (owner-approved 2026-09-13): S0
  done, S1–S9 open; no device mutation before S4's recovery contract is Sol-reviewed.
- **Night Light fix** — `docs/plans/NIGHT_LIGHT_CIRCADIAN_FIX.md`, for
  `faded-penguin021/AdvancedAutoBrightness#15` (not a Tideo issue): [x] N1 · [ ] N2 Kelvin bounds
  · [ ] N3 daytime activation, BLOCKED on device evidence · [ ] N4 close-out.
- **Light stalls (Tideo #130, #132)** — `docs/plans/LIGHT_STALL_FIX.md`: [x] R0 · [x] R5 (DC-063)
  · [x] F-G (DC-064) · [x] R1 notification overwrite (DC-065) · [x] R3 diagnostics (DC-066)
  · [x] R2 startup race (DC-067) · [x] R4 watchdog (DC-068) · [x] R6 pending slot (DC-069)
  · [ ] R8 close-out, **next unless R7 reopens** (open question below); RF dropped (plan §5).

## Owner queue

> **Protected section (D-167).** Never delete it, and never silently drop items during compression
> — a ladder guard warns if the header vanishes. Items leave only when done, answered or triaged;
> then delete the item and record the outcome as a Changelog line or a ledger row. How to test an
> item before restating it, and why every session's final message must:
> `docs/RUNBOOK.md` → **Session discipline** 7.
>
> **Plain language here — exempt from the tree's terse, ledger-ID-first register (DB-079, owner,
> 2026-08-23)** because a person decides from it: say what to do, on what, what result means it
> worked, and the command that settles it, with ledger IDs last. Keep the Open questions format —
> fork, options, recommendation (D-167), dated (DA-006); credential leaks and external-content
> escalations land here too.

1. **Backlog, not for this train — extract the 29 hardcoded diagnostic-card labels (DC-040).**
   `WRAPPER_CEILING` in `HardcodedStringCheckTest` freezes them at 29 and may only fall. Settles
   it: `./gradlew :app:testDebugUnitTest --tests '*HardcodedStringCheck*'` — green means the debt
   has not grown, not that it is gone.

2. **[2026-09-18, retargeted 2026-09-22] Tag v1.11.0 when you are ready; tagging is yours.**
   First RUNBOOK §6 wants a green `fdroid-compat.yml` on the release commit, and the first release
   after the AGP 8.13.2 bump owes a one-shot DA-026 check (v1.10.1 was never tagged and is
   superseded). Settles it: `git ls-remote --tags --refs origin 'refs/tags/v1.11.0'` — a hit
   means done.

3. **[2026-09-23] On the next false "manual override" pause, read brightness before pressing
   Resume:** `adb shell settings get system screen_brightness` while Live Debug still shows the
   pause. About 193 means the 12 stuck, so something outside Tideo changed brightness; 241 means a
   dip that reverted by itself, which Tideo paused on because its "settled" value is a re-read
   3 ms later, and fixing that is a settle-window change for you to rule on. Extra Dim is ruled
   out (DC-059…DC-062).

4. **[2026-09-24] Check the proximity change on the phone next time you test a build.** Tideo no
   longer slows brightness while the top of the phone is covered, which is what Tasker does: its
   ×0.1 only ever changed the displayed α (DC-064). If you would rather keep the old slowing as a
   deliberate difference from Tasker, say so; it is one engine change back. Settles it: run
   `DEVICE_TEST_SCRIPT.md` step 13 — brightness tracks as fast covered as uncovered, and only
   Live Debug's "Smoothing α" drops to a tenth.

Open questions:

- **[2026-09-24] Reopen R7, the settling path, before the R8 close-out?** On the OnePlus 13 in
  a dark room, after a flashlight flicker ending dark, one cycle smoothed only from the flicker's
  level to 42 lx (and to 100 lx in a second run), so the screen stayed at the brightness for
  42 lx (32/255) in the dark; every later 0 lx reading is inside the 0.0–0.0 band, so nothing
  moves it again. That is R7's reopen condition in the plan: a completed cycle short of its
  target with no later reading admitted. Options: (a) reopen R7 now, with its Tasker check
  first (the plan's Q2), and hold R8 until it lands; (b) close the train with R8 and keep R7
  for a later release; (c) accept the behaviour. **Recommendation: (a)** — it is the stall
  #132 describes, it now reproduces on demand in about 15 s, and R6 cannot fix it alone.

**This train is `1.11.0` on vc25, its ONE bump (owner, 2026-09-22).** `1.10.1` never shipped, so
DC-057's new capability made the same vc25 a minor. Land further user-facing fixes by editing
`changelogs/25.txt` (500-character cap), never by bumping or by creating `26.txt`; re-open only for
something major, and say so.

## Decided non-items

- **No migration resets an already-snowballed `nightLightTemperature` (owner, 2026-09-21;
  DC-055).** A stored Kelvin cannot be told apart from a setpoint the user genuinely chose, so a
  blanket reset to null would discard real choices while missing contaminated profiles that
  currently have circadian off. DC-055 stops the capture; an affected user clears it with the
  screen's existing "device default" button. Do not propose a one-shot reset.
- **Both backup-fix questions are closed (owner, 2026-09-18; DC-052)** — the pre-fix blast radius
  will not be measured, needing `bmgr` work the owner declines, and no regression test guards
  `android:backupAgent`, one having been dropped as YAGNI; propose neither.
- **The `stop()`/`emergencyStop()` join asymmetry stays** (owner, 2026-09-07; DC-047).
- **Issues #123, #126 and #127 get no reply, and no issue gets one unasked** (owner, 2026-08-24,
  re-confirmed 2026-09-07; DB-082) — a standing rule, and nothing was posted.
- **Three device checks will never be executed (owner, 2026-09-07)** — the Android 12/12L Wi-Fi fix
  (DB-074), the unrecognised-colour-mode button (DB-071, DB-078) and Night Light / always-on failing
  safely (DB-041…DB-043) all need hardware the owner lacks.
- **Graph Metrics is owner-tested; its automated wiring tests are declined** (owner, 2026-09-07;
  DC-001).
- **The x86_64-JDK-on-ARM rule is a local host concern** (owner, 2026-09-07; DC-033,
  `.orch/LOCAL_LADDER.md`).
- Still declined: root changelog, speculative dependency bumps, standalone drift audit, Gradle
  dependency verification, wider session-branch CI, the D-162/DA-021 triage sets (DB-038), the
  superseded Privileged Display schedule and a persisted seed without real reports (D-150–152), a
  grayscale quick action, refresh-rate/OEM keys, manual Extra Dim, panic re-firing after teardown,
  §11.39a C1/C2 as wontfix, the destructive `bmgr restore` re-verification (DB-013), and migrating
  the test-only `ContextsContent` wrapper; the rest is in `docs/plans/REVIEW_TRIAGE_1.9.0.md`.
- **Never synthesise unsupported display values on a device** (DB-071) — use a real settings UI;
  DB-077 is exempt, mask 7 having been written by Tideo v1.9.0.
- Rejected by the #126/#127 plan and not to be reintroduced: keying wake behaviour on
  `ACTION_USER_PRESENT`/unlock (owner, 2026-08-30), a larger fixed or blanket settle window, wake
  baseline adoption, a recent-write token set (D-034/D-051(d)), and auto-learning the device
  maximum.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-09-24 — **Light stalls R6 (DC-069), with its rule review:** a light reading that arrives
  during a cycle or its cooldown now waits in one pending slot, replaced by any newer one, and is
  applied once the cycle and cooldown end, so the last reading of a change is no longer lost; the
  concurrency invariant in `AGENTS.md` changed with it. Both entry tests failed on the old code
  (`expected:<5000.0> but was:<10.0>`). The Light Sensor card adds deferred and replaced counts;
  the brightness line in `25.txt` now names the fix. **Device check passed** the same day on the
  OnePlus 13 (debug build of `938058a`, dark room, a flashlight flickered at the sensor for
  10 s): ending dark, ending on the steady light and ending dark again, the last reading was
  evaluated with no further callback, brightness then held flat, and the counters balanced
  (e.g. 839 = 86 admitted + 568 rejected + 185 replaced). The same runs reproduced F-B twice:
  see the open question on R7.

- 2026-09-24 — **Owner queue item 5 done (DC-066):** the owner ran the dark-wake test on a build
  with the Light Sensor card (OnePlus 13, screen off 3 min). The wake was healthy: WAKE
  registration, first event 0.0 lx within 2 s, about 4 events a second, and no MUTEX or COOLDOWN
  drop. One clean run of an intermittent failure, so H2 stays open; the detail is under the plan's
  H2.

- 2026-09-24 — **Light stalls R4 (DC-068):** a running service in steady light no longer shows as
  stopped 5 s after its task is swiped away; live state is now cleared by service-instance
  ownership, and a predecessor's timer cannot cut a successor's grace. The entry test was red on
  the old code. On the OnePlus 13, R4's and R2's device checks passed, but R4's cannot
  discriminate: a sensor reporting about 4 times a second never stops publishing. No changelog
  line: `25.txt` is at its cap and the defect was never seen in the field.

- 2026-09-24 — **Light stalls R2 (DC-067):** the light sensor registers only once settings have
  loaded, so the reading it sends on registration after a service start is evaluated instead of
  dropped as SETTINGS_NOT_LOADED; a `stop()` that lands mid-start leaves no listener. JVM tests
  only; no device check was run. Also fixed: R1's repeated-start service test, which failed CI at
  `e0cd4c1` because it waited for a brightness that any reapply recomputes; it now waits for the
  curve's own target.

- 2026-09-24 — **Light stalls R3 (DC-066):** Live Debug's new "Light Sensor" card shows the last
  raw callback, each registration and its first event, received/admitted/rejected with the last
  reason and trust setting, and the cycle stage and last result. Pipeline behaviour is unchanged.
  **Device check, daylight only,** at 14:58 on the OnePlus 13, on a debug build of this tree: the
  card rendered, callbacks matched received (#321 / 321), admitted plus rejected summed to received,
  accuracy read 3 with trust off, and the sensor-to-callback lag was 172–230 ms. MUTEX and COOLDOWN
  were not caught as the last reason. The dark-wake test stays owner-queue item 5.

- 2026-09-24 — **Light stalls R1 (DC-065):** a start command reaching a running service, such as
  every settings save or the 15-minute worker, now posts the live `Lux … → brightness …`
  notification (paused title and Resume kept) instead of "Monitoring ambient light". **Device
  check passed** the same day on the OnePlus 13 (debug build of `a50e935`): three REAPPLYs in
  steady light kept `Lux 3532 → brightness 137`, and one more rebuilt the notification (`when`
  moved) with `Lux 3563 → brightness 138` unchanged, where the release build had switched to
  "Monitoring"; the owner-queue item is closed.

- 2026-09-24 — **F-G closed as a parity restore (DC-064):** proximity near damps only the
  reported α, as in Tasker; smoothing, mapping and animation stay undamped. The oracle was already
  right; `LightCycleParityTest` now replays it near, and the test that pinned the old damp is
  replaced.
- 2026-09-24 — **Light stalls: plan re-scoped (R6 in, R7 deferred, RF dropped, as AOSP reports
  light as high accuracy) and R5 landed (DC-063):** a return to the previous light level is no
  longer ignored; the oracle `TaskerReference.lightCycle` is an addition, and no existing oracle or
  vector changed.
- 2026-09-23 — **Light-stall plan (R0), reviewed and rescoped;** a false override pause on unlock
  diagnosed, not fixed (DC-059…DC-062).
- 2026-09-18..22 — **Night Light: snowball closed, anchor on the device's own Kelvin (DC-055,
  DC-056), Kelvin via `color_display` where the key is ignored, making vc25 `1.11.0` (DC-057,
  DC-058); backup-agent fix (DC-052).**
- 2026-09-02..13 — **AMH 9.1.0 → 14.1.0 (DC-029…DC-036, DC-050, DC-051), the runtime rot audit
  (DC-037…DC-049), E2E suite planned.**
- 2026-06-23..08-31 — **v1.0.0 → v1.9.2, then the #126/#127 train (D-096…DC-028).**
