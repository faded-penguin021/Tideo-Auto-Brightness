# STATE — project state & session memory

> **Length guard (DA-004 hysteresis — read before editing).** Thresholds are `STATE_WARN_KB`,
> `STATE_COMPRESS_TO_KB` and `STATE_HARD_KB` in `amh.conf`, deliberately **not** restated as
> numbers here — nothing checks this prose against the config, so a copied number drifts the first
> time a threshold moves. `scripts/ladder.sh` prints the caps whenever it reports the size.
>
> - Grow freely to the soft cap; **no trimming below it.** Above the hard cap the ladder fails.
> - When the guard warns, run **ONE deep pass to the floor** — never to just under the soft cap. A
>   micro-trim re-arms the warning a session later; the wide band IS the debounce. The floor is a
>   ceiling, not a target: landing short means folding MORE completed stages, not micro-trimming.
> - Compression = collapse each completed stage into one Changelog line, fold changelog clusters,
>   move durable gotchas to the append-only ledger, delete narrative prose.
> - **Any** edit that takes the file from above the soft cap to at or below it must reach the
>   floor — a five-byte typo fix included. `STATE_EDIT_DELTA_BYTES` separates an ordinary edit from
>   a short compression pass only while the file is still ABOVE the cap. Never pad the file back up
>   to escape that failure, and never trim a file already under the cap: that pass is invisible to
>   the landing check, and its silence is the absence of a check, not a verdict. Do not reach for a
>   threshold to cover it — it is the SHRINK that is measured, so any "large shrink = compression"
>   rule would fail a session for deleting one resolved Owner-queue item from a healthy file.
> - **Project**, **Current state**, **Decided non-items** and **Changelog** are
>   `STATE_REQUIRED_SECTIONS`: the guard FAILS on a missing or empty one. **Owner queue** is
>   protected separately at WARN level — never delete it, never silently drop items during
>   compression; compress their prose instead. No `##` heading may appear twice (a spliced file
>   duplicates sections, and two copies are two answers that the byte caps cannot see).
>
> That list, plus this file existing at all and a warning when `STATE_EDIT_DELTA_BYTES` is
> malformed, is the whole of what `guard_state_size` and `guard_state_structure` check. The
> structure checks run at EVERY size — only the size guard's landing half goes quiet below the cap.
> Those functions upgrade independently of this file and are the authority: if a later harness adds
> a rung, this paragraph is what goes stale, and nothing checks it against the script. Everything
> else here is prose you are asked to keep, and no guard will catch you breaking it.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

Harness AMH 5.2.0 (DB-027); upstream manifest scripts are immutable. Shipped v1.8.2/vc20, F-Droid
reproducible-build verified (owner, 2026-08-13). Branch carries unreleased v1.9.0/vc21 and
**nothing blocks the tag** — DB-060's crash is device-verified fixed, DB-061 is pre-existing in
`v1.8.2`. Parity checklist and parity gaps empty. Live ledger: `LEDGER_B.md`.

**Resuming cold?** The v1.9.0 train is finished and green on
`claude/pr-116-branch-train-review-c4lo6i`, PR **#117** open against `main` and describing the whole
train (#115 closed, #116 intra-train). v1.9.1 work — for after that merge — sits on
`claude/first-work-item-plan-lqklk4`: all six items of `docs/plans/REVIEW_TRIAGE_1.9.0.md` done
(DB-065…DB-070), one commit each, ladder green, adversarially reviewed. Nothing is half-done; no
review is owed. To ship v1.9.0: squash-merge #117, publish from the GitHub UI, then **delete
`docs/rebuild/DEVICE_TEST_SCRIPT_1.9.0.md`** (ephemeral), folding its results into the standing
script. Do **not** re-open the closed force-stop investigation — read DB-051…DB-060 first; queue
item 1 is a different screen and a different defect.

## Owner queue

> Protected by D-167. Test observable claims before restating them; preserve unresolved items.

1. **Contexts location round-trip — fixed (DB-061), awaiting device verification.** DB-051's
   mechanism on the other screen; pre-existing in `v1.8.2`, so it never blocked the tag. **Owner: on
   device, create a location rule, save, reopen it — the toggle should still be on with the
   coordinates shown, and the list row should name them.**
2. **Unreviewed legislation — CLOSED (DB-064), nothing owed.** DA-005's one-level-of-meta rule stops
   the regress there, by rule and not omission. **Owner: the judgement call to ratify or overrule
   before squash-merging #117.**
3. **Provenance manifest: one record removed (`ProfilesScreen.kt` — its only `// Tasker` line was
   wrapped prose the change deleted), one removed then restored (`MiscScreen.kt` — its marker is
   still in the tree and states Tasker behaviour the port matches, the stated keep-criterion).**
   14 no-coordinate records (an earlier "17" was wrong; it was 15). Hand-edited, never regenerated
   from a mid-change tree (DB-032).
4. DB-041…DB-043's unavailable-feature boundary still unverified (B1 BLOCKED twice): the owner's
   device reports Night Light/AOD available. Needs hardware reporting them unavailable.
5. **v1.9.1 device checks. Owner:** (a) stay-awake OFF on device, then enable Tideo's toggle and
   Apply → `settings get global stay_on_while_plugged_in` reads **15** (DB-065). It must be the
   off→on transition: DB-068's diff-write skips the write when the device already reads non-zero,
   so an already-on device proves nothing. (b) on a device showing a non-AOSP color-correction
   mode, the picker carries the preservation notice and an unrelated Apply leaves the mode alone
   (DB-066/DB-069).

Open questions: none. Owed reviews: none.

Incoming: force-stop location defect **closed as no app defect** (owner, 2026-08-17), cold-GNSS
warm-up → DB-059. Device rounds: 1.8.2-debug 49/5/2/3; 1.9.0 `7970765` 11/1/3, all owner-closed;
`fc35a6e`/`036ec77` → DB-054…DB-058; `12b5a21` → DB-060, verified fixed 2026-08-17.

## Decided non-items

- Repo/process declines: root changelog, speculative dependency bumps, standalone drift audit,
  Gradle dependency verification, wider session-branch CI, the D-162/DA-021 triage sets. SHA pinning
  left this list when Dependabot supplied a refresh path (DB-038).
- Privileged Display declines: per-toggle scheduling, persisted seed without real reports, grayscale
  quick action, refresh-rate/OEM keys, manual Extra Dim (D-150–152). `ContextsContent` stays
  test-only — migrating its 13 test sites buys nothing and risks its a11y coverage (v1.9.0 review
  `WAIT-MINOR-003`).
- Panic re-firing after teardown (1.9.0 D4): owner-closed as the still-installed Tasker project's
  own prof769 gesture, a third armed listener of the D-128 class. Reopen only on a report from a
  device with no sibling armed.
- §11.39a C1/C2 (external Night Light tracked twice): wontfix — no tile on the device, unit-tested.
- Never repeat whole-device backup/restore verification: `bmgr restore` damaged unrelated apps;
  callback invocation is accepted unverified residual (DB-013).

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-08-18 — **v1.9.1 triage items 1–6 done (DB-065…DB-070).** Stay-awake writes AOSP's full
  `AC|USB|WIRELESS|DOCK` mask instead of narrowing it to 7; an unrecognized daltonizer mode reads as
  unrepresentable rather than OFF and survives an unrelated Apply; `activeFix` releases its location
  listener on the `SecurityException` path; the direct Apply diff-writes like the coordinator. The
  review's canonical-7 test was **not** adopted (it would re-run DB-049). A fresh-context
  adversarial pass over the finished branch then found three real defects in that work: two code
  (DB-069), one an Owner-queue check that could not fail (DB-070). Per-item detail and the severity disagreements: `docs/plans/REVIEW_TRIAGE_1.9.0.md`.
- 2026-08-17..18 — **Harness legislation reviewed to a close (DB-062…DB-064); location and Contexts
  defects fixed (DB-057…DB-061).** A one-time inline-Python edit advisory plus two DA-005 passes
  that found thirteen further defects, nine confirmed by running (suite 108 → 111); cancelled fixes
  no longer report as failed, a last-known fix under an hour old is accepted, the "Use current
  location" crash fixed and its class guarded by `format-args.sh`, and the Contexts location
  round-trip fixed (locale decimal separator).
- 2026-08-11..16 — **v1.9.0/vc21 train (DB-028…DB-051).** Privileged Display shows the device, not
  the stored profile, and cannot write what the framework reports unsupported (read-back with a
  re-merge gate, capability gates, Disable HDR as a stored preference). Panic confirms once at every
  entry point; dropped control commands explain themselves at level 8. Repo tier: Kotlin prose moved
  to the `.md` tier behind a fail-closed comment budget, Actions SHA-pinned with a Dependabot
  refresh path, tokens scoped, AMH 4.1.0 → 5.2.0.
- 2026-06-23..08-10 — **v1.0.0 → v1.8.2 and AMH convergence (D-096…D-176, DA-001…DA-044,
  DB-001…DB-027).** Rebuild/release/glue gates, F-Droid, hardening, Tasker parity, security review,
  triage and AMH upgrades through 5.2.0.
