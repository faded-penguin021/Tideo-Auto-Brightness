# STATE — project state & session memory

> **Length guard (DA-004).** Thresholds are in `amh.conf`; the compression rules are
> `docs/RUNBOOK.md` → **Working-memory compression**, and they bind whether or not you follow this
> pointer. That section also says what may sit in `Current state` at all: what stays true of the
> checked-out tree, never world-controlled status (merged, tagged, released, PR/CI, deployments,
> remote branches, forge settings) as current truth. Point at a live probe, route an external
> action to the Owner queue, or scope a past observation to when it was seen. The Changelog and
> ledger pointers are historical storage and are exempt. Prose-only — no guard judges it.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

<!--
Write what a fresh clone of THIS COMMIT would still find true. Test each sentence: would it hold
tomorrow, under another branch name, after forge state had moved? If not, it belongs at a live
probe, in the Owner queue, or scoped as a dated observation — not here as fact. Do not write
"released", "tagged", "merged", "CI is green" or "protection is configured" as current state.
Name the live ledger VOLUME, never its latest row id — every append moves that.
-->

Harness AMH 14.0.0 (DC-029), upstream manifest scripts immutable; live ledger `LEDGER_C.md`.
Scripts and binding prose are both 14.0.0, so `AMH_PROSE_VERSION` equals `AMH_VERSION` and
`doc-facts.sh` is quiet on the pair (DC-031, DC-036).

**Resuming cold?** Release standing is NOT recorded here — the session banner computes it live via
`scripts/session-facts.sh` (DC-030), settled by hand with
`git ls-remote --tags --refs origin 'refs/tags/v*'`. Nothing stops a later session re-caching a
release number; that gap is unguarded.

This branch carries Graph Metrics (DC-001), the LEDGER_C rollover, the executed #126/#127
override-attribution work (DC-002…DC-028), the harness units DC-029…DC-036 and the runtime rot
audit DC-042…DC-046. Device rounds on 1.10.0-debug vc24 are **all closed**, the owner having ruled
no fix on the 0–4095 scale that sits below the app-facing Settings API, which freezes the
conversion path as built; readings are in the rows and the checks in `DEVICE_TEST_SCRIPT.md` §2, and
a later build owes its own run (DC-011…DC-013, DC-025…DC-028, DB-083). No round script is alive
(RUNBOOK §6, DB-010), the force-stop investigation stays closed (DB-051…DB-060), and Scorecard.dev
is a run-once local input, not a score or CI gate.

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

1. **Backlog, NOT for this train — extract the 29 hardcoded diagnostic-card labels the widened i18n
   ratchet surfaced (DC-040).** Frozen at 29 by `WRAPPER_CEILING` in `HardcodedStringCheckTest`; the
   ceiling may only fall. Settles it:
   `./gradlew :app:testDebugUnitTest --tests '*HardcodedStringCheck*'` — green means the debt has
   not grown, not that it is gone.

Open questions: none. Both stood answered on 2026-09-07 — the teardown join asymmetry accepted as
it is (DC-047), the rename taken (DC-048).

**Decided (owner).** This train ships as a **minor**, `1.10.0` / vc24 (2026-08-30,
`app/build.gradle.kts`).

## Decided non-items

- **The `stop()`/`emergencyStop()` join asymmetry stays as it is (owner, 2026-09-07; DC-047).**
  Ordinary teardown cancels the consumer without joining and then undoes its effects, so a late
  write can survive the cleanup. Accepted rather than fixed: `onDestroy()` cannot suspend, and the
  alternatives were declined. Read DC-047 before "fixing" it — it is a decision, not an oversight.
- **Issues #123, #126 and #127 get no reply, and no issue gets one unasked** (owner, 2026-08-24 for
  #123, re-confirmed 2026-09-07; DB-082). Nothing was posted. This is the standing rule, not a
  pending item: never comment on a forge issue without the owner saying so first.
- **Three device checks will never be executed (owner, 2026-09-07).** The Android 12/12L Wi-Fi fix
  (DB-074, §8 24), the unrecognised-colour-mode button (§11 32c, DB-071, DB-078) and Night Light /
  always-on failing safely (DB-041…DB-043) each need hardware the owner does not have and does not
  expect to get. They are unverified by construction, not pending: do not re-raise them as queue
  items, and do not synthesise a fake value to force any of them (DB-071).
- **Graph Metrics is owner-tested and works (owner, 2026-09-07); its automated wiring tests are
  declined.** Nothing covers `ChartCanvas` calling the sink, the sink being null below level 7, or
  the dedupe suppressing a repeat draw — `ChartCanvasTest` exercises `graphSignature`'s maths only.
  The owner's device testing is the evidence, and that is accepted as sufficient (DC-001).
- **The x86_64-JDK-on-ARM rule is a local host concern and is not tracked here (owner, 2026-09-07).**
  It belongs to whichever machine runs the ladder, not to the tree. `.orch/LOCAL_LADDER.md` carries
  both hosts and their setup; DC-033 keeps the measurement and the reasoning error behind it, and
  the session banner reports the JDK it finds.
- Repo/process declines: root changelog, speculative dependency bumps, standalone drift audit,
  Gradle dependency verification, wider session-branch CI, the D-162/DA-021 triage sets (DB-038).
- Still declined: the superseded Privileged Display schedule and a persisted seed without real
  reports (D-150–152), a grayscale quick action, refresh-rate/OEM keys, manual Extra Dim, panic
  re-firing after teardown, §11.39a C1/C2 as wontfix, the destructive `bmgr restore` re-verification
  (DB-013), and migrating the test-only `ContextsContent` wrapper; the rest of that triage is in
  `docs/plans/REVIEW_TRIAGE_1.9.0.md` (`WAIT-MINOR-003`).
- **Never synthesise unsupported display values on a device** (DB-071); use a real settings UI.
  DB-077 is exempt because mask 7 was written by Tideo v1.9.0 and §11 32a is device-verified.
- Rejected by the #126/#127 plan, not to be reintroduced: keying wake behaviour on
  `ACTION_USER_PRESENT`/unlock (owner, 2026-08-30), a larger fixed or blanket settle window, wake
  baseline adoption, a recent-write token set (D-034/D-051(d)), and auto-learning the device
  maximum.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-09-07 — **The monthly grouped github-actions bump, checked by hand and its prose repaired
  (PR #129).** Four updates over five workflows: `setup-java` 5.7.0 -> 6.0.0 at all five call sites,
  `codeql-action/init` and `/analyze` 4.37.7 -> 4.37.9, `action-gh-release` 3.0.2 -> 3.0.3. Every
  bumped tag was resolved to its commit against the forge and equals the pin, which is the one layer
  `action-pins.sh` cannot reach; three of the four are single-call-site, so nothing else would have
  caught a stale marker on them. The `setup-java` major is inert here — the ESM migration is not
  user-facing, `jdkFile` keeps an alias we never used, and the Zulu -> Azul switch only touches
  `distribution: zulu` while all five sites use temurin — and `node24` was read from `action.yml` at
  the pinned SHA itself. The Node 24 policy blocks in `build.yml` and `fdroid-compat.yml` still said
  `setup-java@v5`, the RUNBOOK 8 step 3 failure exactly, and now say v6. `docs/RUNBOOK.md` carries
  the same stale major in its CI-triage example; it is a rule file, so that correction is owed
  separately rather than smuggled in here.

- 2026-09-07 — **A scoped rot audit of the runtime pipeline core (DC-042…DC-046).** A fresh-context
  reviewer was pointed at eight named runtime files rather than at the repository, and found
  override detection left armed across hibernate, a D-163 clear gated on a condition `onScreenOff()`
  already satisfies, an admission gate publishing before it booked, and two dead members; the
  mandatory glue review then returned NOT CLEAN on that fix and earned DC-046. The
  `stop()`/`emergencyStop()` join asymmetry it also found is an Open question above, not a fix.
  Verification: full ladder green — `:app` 674, `:platform` 290, `:domain` 116, 0 failures, on an
  x86_64 JVM under emulation, with the DC-042, DC-043 and DC-046 tests each shown to FAIL against
  their unfixed code. `theAdmissionCapIsExactlyMaxPending` deliberately does not discriminate and
  pins a boundary instead, DC-044's interleaving is reasoned rather than pinned, and on-device
  behaviour stays owner-verified. The queue item carrying the discharged `845bb75` DA-005 review
  left the queue in the same session, tested rather than restated: `HARNESS_LOCAL.md` now reads
  "Seven of the eight fail closed", so the contradiction DC-035 caught is gone from the tree.
- 2026-09-07 — **DC-042 is device-confirmed in both directions, and it SHIPPED (DC-049).** Sleep,
  write `screen_brightness` over adb, wake: the 1.9.2 release pauses, `c550b5f` does not. Landed as
  check 10e, whose control half is the old build itself — the one thing that distinguishes a fixed
  build from one that has quietly stopped detecting overrides. `changelogs/24.txt` now says so to
  users, since the pause was reachable by anyone whose OEM writes brightness during sleep.
- 2026-09-07 — **Both open questions answered: the teardown race accepted, the rename taken
  (DC-047, DC-048).** `stop()` still cancels without joining and DC-047 records why that is a
  decision rather than an oversight, since the next reader will meet it beside an
  `emergencyStop()` that does join. The rename landed in full — `settingsApiMax`,
  `requestedSettingValue`, `readBackSettingValue`, the two Live Debug labels and their resource
  keys, and §2's check table — proven behaviour-neutral by reversing the substitution and getting
  all five files back byte-identical, with a light glue-review pass CLEAN.
- 2026-09-07 — **The owner cleared four queue items; one backlog item remains.** The three
  hardware-blocked checks are unexecutable for good and are now a decided non-item rather than a
  standing ask; the no-reply rule on #123/#126/#127 moved to the same section as the standing rule
  it always was; Graph Metrics is owner-tested and working, so its automated wiring tests are
  declined and the device testing is the accepted evidence; and the x86_64-JDK rule left the tree as
  a local host concern, kept in `.orch/LOCAL_LADDER.md` with DC-033 holding the reasoning. Only the
  29-label i18n backlog is still queued.
- 2026-09-06 — **A fresh-context review of the PR #128 diff and the four fixes it earned
  (DC-037…DC-041).** The settle-window gate moved after the suspending settings read;
  `OverrideDiagnostic` carries `modeRecovered`; the Brightness Writes card renders the write the
  EVENT captured; and the i18n ratchet gained a second check over this repo's own wrapper
  composables, 12 labels extracted and 29 frozen. The refused-tail baseline is **ruled no-change**
  (owner, 2026-09-06): DC-008 stands and a test pins it.
- 2026-09-02..04 — **The harness train AMH 9.1.0 → 14.0.0, its prose-debt guard and the Codex rail
  (DC-029…DC-036).** Shipped scripts and manifest copied; `AMH_PROSE_VERSION` plus a `doc-facts.sh`
  warn/fail tier carried the scripts-ahead-of-prose split until the owed seed prose landed and
  closed it; the Codex hook claim was measured on 0.152.1/0.153.2 and reworded to
  declared-but-not-observed; and the DA-005 review owed on `845bb75` returned NOT CLEAN.
- 2026-06-23..08-31 — **v1.0.0 → v1.9.2 shipped, then the #126/#127 override-attribution train
  executed and fully read on a device (D-096…DC-028).** `write()` became a transaction reporting
  what Android STORED, feeding both detectors, the baseline and the animation band; the commit guard
  gained a ±1 domain deadband and a `MIN_SETTLE_MS` floor; and Live Debug gained the Brightness
  Writes card that checks §2 10b–10d read from.
