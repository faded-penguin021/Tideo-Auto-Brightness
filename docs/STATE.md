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

Harness AMH 9.1.0 (DB-073), upstream manifest scripts immutable; live ledger `LEDGER_B.md`; parity
checklist and parity gaps empty.

**Resuming cold?** **v1.9.1/vc22 is the newest release** (owner, 2026-08-20; tag on `main`), and
this branch carries unreleased **v1.9.2/vc23** — the AppOps cleanup plus the post-v1.9.0 review
fixes below, device-verified 2026-08-23. Nothing is half-done, no ephemeral device script is
outstanding, and what stands between vc23 and a release PR is the rule-review obligation: every
round so far is answered, and any further rule-file edit — this file's length-guard preamble and
Decided non-items included — owes its own pass. Do **not** re-open
the closed force-stop investigation (DB-051…DB-060), and treat Scorecard.dev as a run-once local
input rather than a retained score or CI gate, its two surviving rails being RUNBOOK playbook 8's.

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

4. **Please check on a phone: pinning a location while the date stays live.** On the Circadian
   screen, tap "Live date" and then "Set fixed" with coordinates filled in. It worked if the status
   line reads "Fixed location: … (live date)" rather than naming a date, and if entering Sydney
   (`-33.87` / `151.21`) in northern summer makes the daylight window on the curve SHORT — a long
   one would mean the date got pinned too. Full steps are §7 21a. (DB-084.)

Open questions: none. Owed reviews: the rule-review protocol ran on this branch's rule-file changes
on 2026-08-23, and again on each round of fixes, because a triage commit that edits rule files owes
a pass of its own. Every finding is fixed; the per-round counts and what each found are in those
commit bodies. Any further rule-file edit here owes another pass before merge.

Answered 2026-08-23: the comment-budget increase is accepted on condition the source pointers stay
terse, since the rule exists to stop prose living in two places. Two numbers settle it, and they
count different things, so do not read either as the other. The new pointer comments are one line
each except one two-line block: `git diff 5b46f88..HEAD -- 'app/**/*.kt' 'platform/**/*.kt' | grep
-E '^\+[[:space:]]*//'` lists all fourteen. The guard counts KDoc and block comments too, so by its
own measure the change adds 22 lines (app 2403 → 2417, platform 306 → 314) — while the re-baseline
those justify ratifies 129 app lines, because the margin was already spent when they landed
(DB-081).

Closed 2026-08-24: the wake false-pause fix is **device-verified on 1.9.2-debug `a0d2650`** (owner),
pre-fix and post-fix, using §2 10a's injected trigger — the pre-fix build pauses, the post-fix one
does not, and the control (inject outside the window) still pauses on both. The owner then pointed
out that 10a's original lock-and-wake wording could not fail on a device that never re-asserts
brightness; rewritten, and the lesson is DB-083. Issue #123 is not yet answered — see below.

Closed 2026-08-23: the stay-awake fix is **device-verified on 1.9.2-debug vc23** (owner) — all three
checks of §11 32a passed, so DB-077 and DB-078 are confirmed on hardware and the DB-065 dock bit now
reaches a device upgrading from v1.9.0.

Closed earlier: DB-061, the DB-064 legislation call, and the provenance-manifest note (owner,
2026-08-18); the force-stop location defect, as no app defect (owner, 2026-08-17). Device rounds
through 1.9.1-debug vc22 are all owner-closed and their findings are ledger rows
(DB-054…DB-060, DB-065, DB-071, DB-072).

## Decided non-items

- Repo/process declines: root changelog, speculative dependency bumps, standalone drift audit,
  Gradle dependency verification, wider session-branch CI, the D-162/DA-021 triage sets. SHA pinning
  left this list when Dependabot supplied a refresh path (DB-038).
- Privileged Display declines: per-toggle scheduling, persisted seed without real reports, grayscale
  quick action, refresh-rate/OEM keys, manual Extra Dim (D-150–152). `ContextsContent` stays
  test-only — migrating its 13 test sites buys nothing and risks its a11y coverage.
- Panic re-firing after teardown (1.9.0 D4): owner-closed as the still-installed Tasker project's
  own prof769 gesture, a third armed listener of the D-128 class. Reopen only on a report from a
  device with no sibling armed.
- §11.39a C1/C2 (external Night Light tracked twice): wontfix — no tile on the device, unit-tested.
- Never repeat whole-device backup/restore verification: `bmgr restore` damaged unrelated apps;
  callback invocation is an accepted unverified residual (DB-013).
- **Never synthesise an unsupported value in a display key on a real device** (DB-071): no
  `settings put` of anything no AOSP path writes — daltonizer matrices, HDR format lists,
  `reduce_bright_colors_level`. Two owner devices have been left needing blind recovery. The ban is
  on writing the row yourself, not on the state: reaching one through a real settings UI is always
  allowed, and where both are described, the UI is the only sanctioned route. The
  unrecognised-daltonizer path (DB-066) is unit-tested only and an accepted unverified residual:
  observe it read-only if a device ever reports such a mode by itself (§11 32c). DB-045's partial
  HDR row is NOT in that position — Developer options → Disable HDR formats produces it through a
  real settings UI, which is what §11 32b has the owner do. **Nor is DB-077**, and this bullet must
  not be read as covering it: `stay_on_while_plugged_in` at 7 is the mask Tideo itself shipped up
  to v1.9.0, so a device upgrading with the toggle already on is holding it in the ordinary course,
  and staging it reproduces a state this app produced rather than inventing one no software writes.
  §11 32a tells the owner to do exactly that, and the check passed on device (2026-08-23).

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-08-23 — **Rule review of the branch's legislation ran and its findings are fixed (DB-080,
  DB-081).** A fresh-context reviewer at the strongest tier found nine; the load-bearing ones were
  DB-074's lint fix landing in `verify.sh` only while both release workflows and the RUNBOOK still
  linted `:app` alone, playbook 8 still saying "nothing re-checks them" about a rail that now has a
  guard, the comment-budget re-baseline crediting this change for 129 lines of which 14 are its own,
  the length-guard preamble losing the sentence that said which of its bullets no guard enforces,
  and DB-077 filed under DB-071's never-synthesise residual although its state is a mask Tideo
  itself wrote and the check passed on device. Prose-only fixes plus two workflow lines; no app
  behaviour changed.

- 2026-08-24 — **Circadian: a fixed location with a live date is now settable (DB-084).** The store
  and `CircadianWindowProvider` had always resolved date and location independently, but the card's
  Set button took a non-null date and the date control had no "unset" rendering, so pinning a
  location always pinned that day too — Tasker's picker allows it, which is how the owner noticed.
  A `dateFixed` flag plus a "Live date" button gives the date the same three-state reading the
  status line already printed. Owner device check is §7 21a; unit-tested at card and provider level.
- 2026-08-24 — **Issue #123: false "manual override" pause on wake, fixed (DB-082).** `hibernate()`
  nulls both lux fields, so on screen-on `setInitialBrightness` returned at its first line and never
  reached `armInitialSettle` — the F64/D-126 settle window was armed on every transition except the
  one where the framework re-asserts brightness itself, which then read as a slider move. The window
  is now armed before the lux guard, again on the receiver thread at `onScreenOn()`, and re-checked
  at commit in `handleOverride`. `main`'s AUTOMATION.md edit merged in. D-049 #2 (the single-latest
  self-write marker) is still the deeper hole and is untouched.
- 2026-08-23 — **Post-v1.9.0 review: five findings fixed on the unreleased vc23 (DB-074…DB-078),
  and the Owner queue is plain-language by rule (DB-079).** `:platform` had never been linted,
  hiding two `NewApi` errors on `readNBytes` that left the root and dumpsys SSID strategies silently
  dead on Android 12/12L. DB-065's dock bit had been cancelled by DB-068's diff-write on every
  device carrying v1.9.0's own mask of 7. The preserved-state notices could not write the value
  their own control showed, `hasUsageStatsAccess` branched on a misread of the API-36 stubs, and
  playbook 8's marker↔SHA rails had no guard. Comment budget re-baselined for the new declarations,
  after cutting the new comments to one-line pointers took the overshoot from 38/16 lines to 14/8.
  The stay-awake half is owner-verified on device the same day; the rest is unit-tested or blocked
  on hardware.
- 2026-08-21..22 — **AMH upgraded 5.2.0 → 9.1.0 (DB-073) and the constitution rewritten in natural
  prose.** Shipped scripts and manifest copied from the exact `amh-v9.1.0` tag; sentence floors
  added beside the byte backstops; adapters reconciled with the new secret, lifecycle, review and
  publication rails. `AGENTS.md` kept every rule, fact, citation and enforcement boundary — no
  policy changed.
- 2026-06-23..08-20 — **v1.0.0 → v1.9.1 shipped (D-096…D-176, DA-001…DA-044, DB-001…DB-072).**
  Rebuild, release and glue gates, F-Droid compatibility, hardening, Tasker parity, security review,
  the Privileged Display device-truth rework, the v1.9.0 review triage, RUNBOOK playbook 8, and AMH
  upgrades through 5.2.0. Per-item detail is in the ledger and
  `docs/plans/REVIEW_TRIAGE_1.9.0.md`.
