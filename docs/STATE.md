# STATE — project state & session memory

> **Length guard (DA-004 hysteresis — read before editing).** Thresholds live in `amh.conf` and
> `scripts/ladder.sh` prints them with the size; `guard_state_size` and `guard_state_structure` are
> the authority, so read them rather than any summary — the mechanised/prose split has been stated
> wrongly here twice. Grow freely to the soft cap; on a warning run ONE deep pass to BOTH floors,
> folding whole completed stages rather than shaving words, which the sentence floor cannot be met by.
> Three ways to pass while defeating the rule: nothing reads WHAT a pass deleted, nothing sees a trim
> or a pad of a file already under the cap, and the Owner queue is protected by its heading existing,
> never by its items surviving — so never drop an item while compressing, compress its prose instead.
> The guard FAILS on a missing or empty **Project**, **Current state**, **Decided non-items** or
> **Changelog**, WARNs on **Owner queue**, and rejects a `##` heading used twice.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

Harness AMH 9.1.0 (DB-073), upstream manifest scripts immutable; live ledger `LEDGER_C.md`.

**Resuming cold?** **v1.9.2/vc23 is the newest release** — tag `v1.9.2` → `c7c96dc` on `main`
(`git ls-remote --tags origin` settles it), so every rule-review round is answered. This branch is
bumped to **`1.9.3` / vc24** with `fastlane/…/changelogs/24.txt` and carries the Graph Metrics debug
restoration (DC-001), the LEDGER_C rollover, a test-only compiler-warning cleanup, and the #126/#127
override-attribution work below. No round script is alive: 1.9.2 shipped, so
`DEVICE_TEST_SCRIPT_1.9.2.md` was retired into `DEVICE_TEST_SCRIPT.md` §11 step 39d and §7 21a
(RUNBOOK §6, DB-010). Do **not** re-open the closed force-stop investigation (DB-051…DB-060), and
treat Scorecard.dev as a run-once local input rather than a retained score or CI gate, its two
surviving rails being RUNBOOK playbook 8's.

## Active work

**#126/#127 override attribution**, plan `docs/plans/OVERRIDE_ATTRIBUTION_1.9.3.md` (owner-approved,
revision 3), executing on `claude/override-detection-race-condition-mli9ia`. Segments run
sequentially and each ends ladder-green, committed and pushed.

- [x] A — this checklist plus the owed DA-004 compression pass.
- [ ] B — change 1: transactional `write()` returning `BrightnessWriteResult`, `selfWriteInProgress`,
      `isManualMode()`, `forceManualMode(): Boolean`, `isOnScreenSelfWrite()` deleted, OEM test seam.
- [ ] C — changes 1b/2/7a: `AnimationOutcome`, acknowledged baselines, detector source on the event.
- [ ] D — changes 3/4/5: settle-delay floor, ±1 commit deadband, mode-aware attribution in
      `OverrideRules.shouldCommitPause`.
- [ ] E — change 7b/7c: `lastBrightnessWrite`, `overrideDiagnostic`, one Live Debug card.
- [ ] F — recording: ledger rows, `parity_gaps.md`, device checks, semver call, changelog.

Change 6 is WITHDRAWN by the plan and `INITIAL_SETTLE_MS` stays 1500. The plan file is **retained by
owner instruction (2026-08-30)**, not deleted at the final segment as playbook 5 would have it; its
durable content still goes to the ledger and the Changelog.

## Owner queue

> Protected by D-167. Test observable claims before restating them; preserve unresolved items.
>
> **Write every item in plain language — this section is exempt from the terse, ledger-ID-first
> register the rest of the tree uses (DB-079, owner, 2026-08-23),** because its readers include a
> person deciding what to do, not only a maintainer reconstructing a rationale. Say what to do, on
> what, and what result would mean it worked; put ledger IDs at the end as a reference, never as the
> subject of a sentence; rewrite any item that cannot be understood without opening another file. The
> exemption covers register and nothing else, so still name the command that settles an observable
> claim (AGENTS.md) and keep the Open questions format — the fork, the options, a recommendation
> (D-167), date-stamped (DA-006). Leaked-credential entries and external-content escalations land
> here too, so not everything in this section is a request to the owner.

1. **Nothing to do — two checks are blocked on hardware.** The Android 12/12L Wi-Fi fix needs a
   phone that old and the owner has none (DB-074, §8 step 24). The unrecognised-colour-mode button
   only appears if a phone reports a mode Android does not know, and none do — never force one by
   writing a fake value (DB-078's colour-mode half, §11 32c, DB-071). Both stay unverified on
   purpose.
2. **Nothing to do — waiting on a Samsung.** Whether Night Light and always-on display fail safely
   when Android reports them unavailable; every phone to hand reports them available. Blocked three
   times already, so parked until such a device exists. (DB-041…DB-043.)
3. **Nothing to do — issues #123, #126 and #127 get no reply.** Owner's decision, 2026-08-24 for
   #123 and carried forward by the #126/#127 plan: do not post to the tracker. Nothing was posted.
   Do not comment on them in a later session without the owner saying so first. (DB-082.)
4. **Verify the Graph Metrics debug flash on a device (1.9.3-debug vc24).** Set Live Debug to level 7
   (Graph Metrics), open any graph screen (Curve, Reactivity, …), and confirm a `[Graph Metrics]
   redraw X.Yms` flash on each (re)generation: editing a curve setting should re-flash; scrub-dragging
   the graph should NOT (unchanged data is deduped). The JVM tests cover the dedupe and the level-7
   gate; only the on-device flash is unverifiable locally (no emulator). (DC-001.)

Open questions: none. Everything raised through 2026-08-26 is closed and its detail is in the named
ledger rows: the LEDGER_C rollover, the comment-budget increase (DB-081), the action-pins findings
(DB-085), the vc23 device rounds now folded into `DEVICE_TEST_SCRIPT.md` §7 21a and §11 39d
(DB-084, DB-077, DB-078), the wake false-pause fix and its DB-083 lesson that a device check must be
able to fail on a well-behaved phone, and every earlier round through vc22 (DB-054…DB-072). The
2026-08-26 owed fresh-context review of `b462e56..HEAD` is discharged by this train's glue review.

## Decided non-items

- Repo/process declines remain: root changelog, speculative dependency bumps, standalone drift
  audit, Gradle dependency verification, wider session-branch CI, and the D-162/DA-021 triage sets.
  SHA pinning left this list when Dependabot supplied a refresh path (DB-038).
- The superseded Privileged Display schedule and a persisted seed without real reports remain
  declined (D-150–152), as do a grayscale quick action, refresh-rate/OEM keys, and manual Extra Dim.
  The test-only `ContextsContent` wrapper stays test-only: migrating its 13 test sites buys nothing
  and risks its accessibility coverage (`docs/plans/REVIEW_TRIAGE_1.9.0.md`, `WAIT-MINOR-003`).
- Panic re-firing after teardown remains declined as the sibling Tasker project's own gesture;
  §11.39a C1/C2 remains wontfix; destructive `bmgr restore` verification must not be repeated
  (DB-013). The retained triage detail is in `docs/plans/REVIEW_TRIAGE_1.9.0.md`.
- **Never synthesise unsupported display values on a device** (DB-071); use a real settings UI.
  DB-077 is exempt because mask 7 was written by Tideo v1.9.0 and §11 32a is device-verified.
- Rejected by the #126/#127 plan, not to be reintroduced: keying wake behaviour on
  `ACTION_USER_PRESENT`/unlock (owner, 2026-08-30), a larger fixed or blanket settle window, wake
  baseline adoption, a recent-write token set (D-034/D-051(d)), auto-learning the device maximum.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-08-30 — Owner-approved #126/#127 override plan recorded as
  `docs/plans/OVERRIDE_ATTRIBUTION_1.9.3.md` and now executing here; RUNBOOK 4/5 give plans one home
  and cover cross-session handoff. Added the `## Active work` segment checklist and ran the DA-004
  compression pass it owed.
- 2026-08-26 — Version bump to `1.9.3` / vc24 (patch, owner-decided — a miscategorisation fix) plus
  `fastlane/…/changelogs/24.txt`; one on-device check pending (Owner queue item 4).
- 2026-08-26 — Restored Graph Metrics debug (%AAB_Debug 7) to timing chart (re)draws, deduped by a
  content signature, and deleted the miscategorised `PipelineCycleRunner` cycle-time emit; rolled the
  ledger to `LEDGER_C.md` in the SAME commit, LEDGER_B having hit its cap (DC-001).
- 2026-08-23..25 — Retired the ephemeral round script once 1.9.2 shipped; cleared all 46 test-only
  compiler warnings with no `src/main` change; closed the action-pins findings (DB-085); DB-084 added
  live date with fixed location; DB-082 fixed wake-settle handling; DB-074…DB-081 fixed review
  findings.
- 2026-08-21..22 — DB-073 upgraded AMH 5.2.0 → 9.1.0 without changing policy.
- 2026-06-23..08-20 — v1.0.0 → v1.9.1 shipped; durable detail is D-096…DB-072.
