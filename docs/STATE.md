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

1. **Nothing to do — three checks are blocked on hardware.** The Android 12/12L Wi-Fi fix needs a
   phone that old (DB-074, §8 24); the unrecognised-colour-mode button needs a phone reporting a
   mode Android does not know, and a fake value must never be written to force one (§11 32c,
   DB-071, DB-078); Night Light / always-on failing safely needs a Samsung (DB-041…DB-043).
2. **Nothing to do — issues #123, #126 and #127 get no reply.** Owner's decision (2026-08-24 for
   #123, carried forward); do not comment without the owner saying so first (DB-082).
3. **Backlog, owner-approved 2026-08-30 but NOT for this train — give the Graph Metrics wiring real
   tests.** Nothing covers `ChartCanvas` calling the sink, the sink being null below level 7, or the
   signature dedupe suppressing a repeat draw, which is why the owner's device sighting is the
   feature's only evidence. Contained Compose work, its own unit (DC-001).
4. **Nothing to do — set `JAVA_HOME` to an x86_64 JDK before the ladder on any ARM host
   (2026-09-02, revised 2026-09-06, DC-033).** An ARM JVM asks for natives nobody publishes for ARM:
   Windows-on-ARM fails 120 of 145 `:platform` on conscrypt, the aarch64 Linux container fails 325
   of 670 `:app` on Robolectric, and `aapt2` is x86_64-only underneath both. `.orch/LOCAL_LADDER.md`
   carries both hosts, the two Windows shell settings that ride along, and the container's
   `libc6-amd64-cross` + `QEMU_LD_PREFIX=/usr/x86_64-linux-gnu`.
5. **Backlog, NOT for this train — extract the 29 hardcoded diagnostic-card labels the widened i18n
   ratchet surfaced (DC-040).** Frozen at 29 by `WRAPPER_CEILING` in `HardcodedStringCheckTest`; the
   ceiling may only fall. Settles it:
   `./gradlew :app:testDebugUnitTest --tests '*HardcodedStringCheck*'` — green means the debt has
   not grown, not that it is gone.

Open questions:

- **[2026-09-07] Teardown does not join the consumer before its compensating writes — which
  contract should change?** `stop()` cancels `consumerJob` then immediately calls
  `dimming.disengage()` and `brightness.restoreMode()`, while the sibling `emergencyStop()` joins
  first; cancellation is cooperative, so a running cycle can reapply dimming or force manual mode
  after the cleanup meant to undo it (D-139's own class). `stop()` cannot simply follow the sibling
  because its primary caller is `AmbientMonitoringService.onDestroy()`, which is not a suspend
  context and where `runBlocking` can hold the main thread for a whole animation. Options: (a) a
  `suspend stopAndJoin()` for the suspend callers only, fixing the minority of sites; (b) a
  torn-down flag the cycle's write path checks, making the writes no-ops; (c) accept it, since
  `onDestroy()` cancels the scope moments later. **Recommendation: (b)** — it covers every call
  site without changing the teardown contract or risking an ANR. Found by the DC-042…DC-046 audit.
- **[2026-08-31] The rename half of the deferred cleanup — take it, or drop it with the other
  half?** The no-fix ruling already declined (i), moving `deviceMax` to `context.resources`
  (DC-019, DC-026). That leaves (ii): rename `deviceMax`/`requestedRaw`/`acknowledgedRaw` to
  `settingsApiMax`/`requestedSettingValue`/`readBackSettingValue`, with Live Debug labels becoming
  "Settings API max" and "Settings value requested" — no behaviour change, just names that say what
  10d proved they are, which is the misreading that produced DC-014 (DC-023).
  **Recommendation: take it, and the hold is discharged** — the only reason to wait was that §2 10b
  cited the present labels verbatim, and 10b passed (DC-027).

**Decided (owner).** This train ships as a **minor**, `1.10.0` / vc24 (2026-08-30,
`app/build.gradle.kts`).

## Decided non-items

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
