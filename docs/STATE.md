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

<!-- Fresh-clone truth only: never world-controlled status (released, tagged, merged, CI,
protection). Name the live ledger VOLUME, never its latest row id. Rules: docs/RUNBOOK.md ->
Working-memory compression. -->

Harness AMH 14.1.0 (DC-029), upstream manifest scripts immutable; live ledger `LEDGER_C.md`.
Scripts and binding prose are both 14.1.0, so `AMH_PROSE_VERSION` equals `AMH_VERSION` and
`doc-facts.sh` is quiet on the pair (DC-031, DC-036). 14.1.0's hand step, the adapter hook shell
pin, is applied (DC-051).

**Resuming cold?** Release standing is NOT recorded here — the session banner computes it live via
`scripts/session-facts.sh` (DC-030), settled by hand with
`git ls-remote --tags --refs origin 'refs/tags/v*'`. Nothing stops a later session re-caching a
release number; that gap is unguarded.

This branch carries Graph Metrics (DC-001), the LEDGER_C rollover, the executed #126/#127
override-attribution work (DC-002…DC-028), the harness units DC-029…DC-036 and the runtime rot
audit DC-042…DC-046. Device rounds on 1.10.0-debug vc24 are **all closed**, the owner having ruled
no fix on the 0–4095 scale beneath the app-facing Settings API, which freezes the conversion path
as built; readings are in the rows, the checks in `DEVICE_TEST_SCRIPT.md` §2, and a later build
owes its own run (DC-011…DC-013, DC-025…DC-028, DB-083). No round script is alive (RUNBOOK §6,
DB-010), the force-stop investigation stays closed (DB-051…DB-060), and Scorecard.dev is a
run-once local input, not a score or CI gate.

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
  write can survive the cleanup; `onDestroy()` cannot suspend and the alternatives were declined.
  Read DC-047 before "fixing" it — a decision, not an oversight.
- **Issues #123, #126 and #127 get no reply, and no issue gets one unasked** (owner, 2026-08-24 for
  #123, re-confirmed 2026-09-07; DB-082). Nothing was posted. Standing rule, not a pending item.
- **Three device checks will never be executed (owner, 2026-09-07).** The Android 12/12L Wi-Fi fix
  (DB-074, §8 24), the unrecognised-colour-mode button (§11 32c, DB-071, DB-078) and Night Light /
  always-on failing safely (DB-041…DB-043) each need hardware the owner does not have. Unverified
  by construction, not pending: do not re-raise them, and do not synthesise a fake value to force
  one (DB-071).
- **Graph Metrics is owner-tested and works (owner, 2026-09-07); its automated wiring tests are
  declined.** Nothing covers `ChartCanvas` calling the sink, the null sink below level 7, or the
  dedupe suppressing a repeat draw — `ChartCanvasTest` covers `graphSignature`'s maths only, and
  the device testing is the accepted evidence (DC-001).
- **The x86_64-JDK-on-ARM rule is a local host concern, not tracked here (owner, 2026-09-07).** It
  belongs to whichever machine runs the ladder; `.orch/LOCAL_LADDER.md` carries both hosts, DC-033
  keeps the measurement and the reasoning error, and the banner reports the JDK it finds.
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

- 2026-09-10 — **AMH 14.0.0 -> 14.1.0.** One MINOR step; no shipped script changed behaviour and
  the release declares no new `amh.conf` key. Its one hand step, the Claude adapter hook shell pin,
  was applied by the owner after the agent was refused the edit, and is unguarded either way
  (DC-050, DC-051).
- 2026-09-07 — **PR #129: the monthly grouped github-actions bump, checked by hand.** Every bumped
  tag was resolved to its pin against the forge, the one layer `action-pins.sh` cannot reach, and
  the `setup-java` major proved inert here; the stale `@v5` policy blocks and the RUNBOOK
  CI-triage example were repaired, that rule-file edit earning a CLEAN DA-005 review.
- 2026-09-07 — **The runtime rot audit, its device confirmation, and the queue clear-out
  (DC-042…DC-049).** A fresh-context reviewer over eight named runtime files found override
  detection armed across hibernate, a redundant D-163 gate, an admission gate publishing before it
  booked, and two dead members; DC-042 was then reproduced on the 1.9.2 release and confirmed fixed
  on 1.10.0-debug, landing as check 10e with the old build as its control. Both open questions
  closed — the teardown join asymmetry accepted (DC-047), the hardware-misnaming rename taken and
  proved behaviour-neutral (DC-048) — and four queue items became decided non-items.
- 2026-09-06 — **PR #128's fresh-context review and its four fixes (DC-037…DC-041).** The
  settle-window gate moved after the suspending settings read, `OverrideDiagnostic` gained
  `modeRecovered`, the Brightness Writes card renders the EVENT's write, and the i18n ratchet gained
  a wrapper-composable check with 29 labels frozen.
- 2026-09-02..04 — **The harness train AMH 9.1.0 -> 14.0.0 (DC-029…DC-036).** Scripts and manifest
  copied, with `AMH_PROSE_VERSION` and a `doc-facts.sh` warn/fail tier carrying the
  scripts-ahead-of-prose split until the owed seed prose closed it.
- 2026-06-23..08-31 — **v1.0.0 → v1.9.2 shipped, then the #126/#127 override-attribution train
  executed and read on a device (D-096…DC-028).** `write()` became a transaction reporting what
  Android STORED, feeding both detectors, the baseline and the animation band; the commit guard
  gained a ±1 domain deadband and a `MIN_SETTLE_MS` floor; Live Debug gained the Brightness Writes
  card that §2 10b–10d read from.
