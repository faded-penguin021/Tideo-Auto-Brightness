# STATE — project state & session memory

> **Length guard (DA-004 hysteresis — read before editing).** Thresholds live in `amh.conf`
> (`STATE_WARN_KB`, `STATE_COMPRESS_TO_KB`, `STATE_COMPRESS_TO_SENTENCES`, `STATE_HARD_KB`) and are
> deliberately not copied here, because nothing would check the copy; `scripts/ladder.sh` prints
> them with the size. The rules, with the reasoning behind each left in `guard_state_size` and
> `guard_state_structure`, which are the authority and upgrade independently of this paragraph.
> **Assume a guard is watching only where you have read that it is.** Some of the bullets below are
> mechanised and some are prose nobody checks, the split is finer than a summary here can hold
> without going stale, and it has been stated wrongly twice. So do not summarise it: read
> `guard_state_size` and `guard_state_structure` when it matters which kind a rule is. Three that
> are worth knowing without looking, because each is a way to pass while defeating the rule —
> nothing reads WHAT a compression pass deleted, nothing sees a trim or a pad of a file that was
> already under the cap, and the Owner queue is protected by its heading existing, never by its
> items surviving.
>
> - Grow freely to the soft cap, no trimming below it; above the hard cap the ladder fails.
> - On a warning, run ONE deep pass to BOTH floors — never to just under the soft cap, and never by
>   shaving words, which the sentence floor cannot be met by. Fold whole completed stages.
> - Compression = one Changelog line per completed stage, changelog clusters folded, durable gotchas
>   moved to the append-only ledger, narrative prose deleted.
> - Any edit crossing from above the soft cap to at or below it must reach both floors, a five-byte
>   typo fix included; `STATE_EDIT_DELTA_BYTES` distinguishes an ordinary edit from a short
>   compression pass only while the file is still above the cap. Never pad the file back up to
>   escape that, and never trim a file already under the cap.
> - Required, and the guard FAILS on a missing or empty one: **Project**, **Current state**,
>   **Decided non-items**, **Changelog**. **Owner queue** is protected at WARN — never delete it and
>   never drop items while compressing, compress their prose instead. No `##` heading twice.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

Harness AMH 9.1.0 (DB-073), upstream manifest scripts immutable; live ledger `LEDGER_C.md`; parity
checklist and parity gaps empty.

**Resuming cold?** **v1.9.2/vc23 is the newest release** — tag `v1.9.2` → `c7c96dc` on `main`
(`git ls-remote --tags origin` settles it), so the pre-ship framing this paragraph used to carry is
discharged and every rule-review round is answered. This branch now carries a real `src/main`
change — the Graph Metrics debug restoration (DC-001) plus the LEDGER_C rollover — beside the earlier
**test-only** compiler-warning cleanup, and is **bumped to `1.9.3` / vc24** (owner decided **patch**,
2026-08-26 — a miscategorisation fix), with `fastlane/…/changelogs/24.txt`. The only behaviour not
checkable without a device is the level-7 Graph Metrics flash (Owner queue item 4). **No round script is alive** — 1.9.2 shipped,
so `DEVICE_TEST_SCRIPT_1.9.2.md` was retired here (RUNBOOK §6, DB-010): its section B is now
`DEVICE_TEST_SCRIPT.md` §11 step **39d** and section A was already §7 21a. Do **not** re-open the
closed force-stop investigation (DB-051…DB-060), and treat Scorecard.dev as a run-once local input
rather than a retained score or CI gate, its two surviving rails being RUNBOOK playbook 8's.

## Owner queue

> Protected by D-167. Test observable claims before restating them; preserve unresolved items.
>
> **Write every item in plain language — this section is exempt from the terse, ledger-ID-first
> register the rest of the tree is written in (DB-079, owner, 2026-08-23).** Its readers include a
> person deciding what to do, not only a maintainer reconstructing a rationale. Say what to do, on
> what, and what result would mean it worked. Put ledger IDs at the end as a reference, never as the
> subject of a sentence. If an item cannot be understood without opening another file, rewrite it.
> The exemption covers register and nothing else. Still required here: name the command that
> settles an observable claim (AGENTS.md), and keep the Open questions format — the fork, the
> options, a recommendation (D-167), date-stamped (DA-006). Not everything here is a request to
> the owner, either: leaked-credential entries and external-content escalations land in this
> section too.

1. **Nothing to do — two checks are blocked on hardware.** The Android 12/12L Wi-Fi fix needs a
   phone that old and the owner has none (DB-074, §8 step 24). The unrecognised-colour-mode button
   only appears if a phone reports a mode Android does not know, and none do — never force one by
   writing a fake value (DB-078's colour-mode half, §11 32c, DB-071). Both stay unverified on
   purpose. DB-078's other half, the stay-awake button, is verified — see below.
2. **Nothing to do — waiting on a Samsung.** Whether Night Light and always-on display fail safely
   when Android reports them unavailable; every phone to hand reports them available. Blocked three
   times already, so parked until such a device exists. (DB-041…DB-043.)
3. **Nothing to do — issue #123 gets no reply.** Owner's decision, 2026-08-24: do not post to the
   tracker. Nothing was posted. Do not re-raise this as an open question, and do not comment on
   #123 in a later session without the owner saying so first. The fix itself is done and verified;
   only the reply was ever in question. (DB-082.)

4. **Verify the Graph Metrics debug flash on a device (1.9.3-debug vc24).** Set Live Debug to level 7
   (Graph Metrics), open any graph screen (Curve, Reactivity, …), and confirm a `[Graph Metrics]
   redraw X.Yms` flash on each (re)generation: editing a curve setting should re-flash; scrub-dragging
   the graph should NOT (unchanged data is deduped). The JVM tests cover the dedupe and the level-7
   gate; only the on-device flash is unverifiable locally (no emulator). (DC-001.)

Open questions: none. **Owed review — re-run once the account limit resets (~11:30pm UTC 2026-08-26):**
the owner lifted the no-spawn rule and asked for a fresh-context glue+rule pass over `b462e56..HEAD`
(includes the AGENTS.md live-volume repoint); it launched but died on the account session limit with
no verdict. Interim cover: the in-context glue-review (commit `8d95efa` body) plus a spot-check that
deleting the `GRAPH_METRICS` cycle-emit lost nothing — `cycleTimeMs` still renders as "Cycle time
(ms)" in `LiveDebugScreen`. The two action-pins findings from the 2026-08-25 pass were fixed with
adversarial fixtures: `docker://` references now require sha256 digests, and valid quoted YAML `uses`
keys enter the same parser. Earlier rule-review rounds are answered; their counts are in their commit
bodies.

Closed 2026-08-26: **the ledger is rolled over to `LEDGER_C.md`** (was the item-4 blocker) — the
LEDGER_B cap left no room for a new row. `LEDGER_C.md` opens with DC-001 (empty volumes fail
`ledger-prefix.sh`, so it ships with its first real row, the Graph Metrics restoration), AGENTS.md's
live-volume line now names it, and the cap rung reads the new live volume. Owner-waived per option
(b); no fresh-context rule-review was spawned (system-prompt no-spawn constraint).

Answered 2026-08-23: the comment-budget increase is accepted on condition the source pointers stay
terse, since the rule exists to stop prose living in two places. Two numbers settle it, and they
count different things, so do not read either as the other. The new pointer comments are one line
each except one two-line block: `git diff 5b46f88..HEAD -- 'app/**/*.kt' 'platform/**/*.kt' | grep
-E '^\+[[:space:]]*//'` lists all fourteen. The guard counts KDoc and block comments too, so by its
own measure the change adds 22 lines (app 2403 → 2417, platform 306 → 314) — while the re-baseline
those justify ratifies 129 app lines, because the margin was already spent when they landed
(DB-081).

Closed 2026-08-25: the last two unverified vc23 changes are **device-verified on 1.9.2-debug vc23**
(owner), both as written in the since-retired `DEVICE_TEST_SCRIPT_1.9.2.md` — DB-084's live date with
a fixed location (§7 21a, including the Sydney short-window check and all three combinations), and the
coordinator stay-awake path, where a profile switch onto a stay-awake-ON profile and a
service stop both leave a custom `stay_on_while_plugged_in` mask alone, with the control confirming
representable states are still written. Both checks now live in `DEVICE_TEST_SCRIPT.md` — §7 21a and
the new §11 **39d** — and the round script was deleted on 2026-08-25 when 1.9.2 shipped
(RUNBOOK §6, DB-010).

Closed 2026-08-24: the wake false-pause fix is **device-verified on 1.9.2-debug `a0d2650`** (owner),
pre-fix and post-fix, using §2 10a's injected trigger — the pre-fix build pauses, the post-fix one
does not, and the control (inject outside the window) still pauses on both. The owner then pointed
out that 10a's original lock-and-wake wording could not fail on a device that never re-asserts
brightness; rewritten, and the lesson is DB-083. The owner decided issue #123 gets no reply — see
the closed queue item.

Closed 2026-08-23: the stay-awake fix is **device-verified on 1.9.2-debug vc23** (owner) — all three
checks of §11 32a passed, so DB-077 and DB-078 are confirmed on hardware and the DB-065 dock bit now
reaches a device upgrading from v1.9.0.

Closed earlier: DB-061, the DB-064 legislation call, and the provenance-manifest note (owner,
2026-08-18); the force-stop location defect, as no app defect (owner, 2026-08-17). Device rounds
through 1.9.1-debug vc22 are all owner-closed and their findings are ledger rows
(DB-054…DB-060, DB-065, DB-071, DB-072).

## Decided non-items

- Repo/process declines remain: root changelog, speculative dependency bumps, standalone drift
  audit, Gradle dependency verification, wider session-branch CI, and the D-162/DA-021 triage sets.
  SHA pinning left this list when Dependabot supplied a refresh path (DB-038).
- The superseded Privileged Display schedule and a persisted seed without real reports remain
  declined (D-150–152), as do a grayscale quick action, refresh-rate/OEM keys, and manual Extra Dim.
  The test-only
  `ContextsContent` wrapper stays test-only: migrating its 13 test sites buys nothing and risks its
  accessibility coverage (`docs/plans/REVIEW_TRIAGE_1.9.0.md`, `WAIT-MINOR-003`).
- Panic re-firing after teardown remains declined as the sibling Tasker project's own gesture;
  §11.39a C1/C2 remains wontfix; destructive `bmgr restore` verification must not be repeated
  (DB-013). The retained triage detail is in `docs/plans/REVIEW_TRIAGE_1.9.0.md`.
- **Never synthesise unsupported display values on a device** (DB-071); use a real settings UI.
  DB-077 is exempt because mask 7 was written by Tideo v1.9.0 and §11 32a is device-verified.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-08-30 — Owner-approved #126/#127 override plan recorded as
  `docs/plans/OVERRIDE_ATTRIBUTION_1.9.3.md`; another session executes it. No code change. RUNBOOK
  4/5 now give plans one home and cover cross-session handoff.
- 2026-08-26 — Version bump to `1.9.3` / vc24 (patch, owner-decided — a miscategorisation fix), plus
  `fastlane/…/changelogs/24.txt`; branch now release-ready. One on-device check pending (Owner queue
  item 4).
- 2026-08-26 — Restored Graph Metrics debug (%AAB_Debug 7) to timing chart (re)draws: `ChartCanvas`
  now flashes each (re)generation under `GRAPH_METRICS`, deduped by a content signature so
  scrub/recompose redraws don't spam; deleted the miscategorised `PipelineCycleRunner` cycle-time
  emit (cycle time stays in `PipelineState.cycleTimeMs`). DC-001. Rolled the ledger to `LEDGER_C.md`
  in the SAME commit — LEDGER_B hit the 1000-line cap so DC-001 had nowhere else to go, and an empty
  volume fails `ledger-prefix.sh`, so the rollover cannot ship rows-less; repointed AGENTS.md's
  live-volume line (owner-waived, old Owner-queue item 4 option b). Full ladder green; glue-review in
  the commit body.
- 2026-08-25 — Retired the ephemeral round script now 1.9.2 has shipped: its coordinator stay-awake
  check is `DEVICE_TEST_SCRIPT.md` §11 **39d** (section A was already §7 21a), and the file is
  deleted (RUNBOOK §6, DB-010). No round script is alive.
- 2026-08-25 — Cleared all 46 test-only compiler warnings (deprecation, opt-in, redundancy) with no
  `src/main` change and no version bump. Robolectric's replacements were taken where they exist, but
  `getLocationUpdateListeners`' "do not test listeners" advice was NOT: it would have deleted the
  D-120/D-122/DB-067 leak guard, so the non-deprecated `getLegacyLocationRequests` carries the same
  count, re-proved by deleting `removeUpdates` and watching both leak tests fail. Sticky-broadcast
  seeding and the `Notification.priority` read keep suppressions — neither has a replacement. No
  ledger row: the live volume is full (see the Owner queue).
- 2026-08-25 — Closed both action-pins review findings: container images require immutable sha256
  digests and quoted YAML `uses` keys can no longer bypass the guard (DB-085).
- 2026-08-25 — DB-084 and the `dfa0c8c` coordinator stay-awake path device-verified on vc23; added
  the ephemeral round script `DEVICE_TEST_SCRIPT_1.9.2.md`.
- 2026-08-24 — DB-084 added live date with fixed location; DB-082 fixed wake-settle handling.
- 2026-08-23 — DB-074…DB-081 fixed review findings; DB-077 preservation now covers service-ON
  profile/stop transitions while DB-078 remains the explicit overwrite path.
- 2026-08-21..22 — DB-073 upgraded AMH 5.2.0 → 9.1.0 without changing policy.
- 2026-06-23..08-20 — v1.0.0 → v1.9.1 shipped; durable detail is D-096…DB-072.
