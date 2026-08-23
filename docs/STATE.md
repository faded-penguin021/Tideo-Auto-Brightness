# STATE — project state & session memory

> **Length guard (DA-004 hysteresis — read before editing).** Thresholds are `STATE_WARN_KB`,
> `STATE_COMPRESS_TO_KB`, `STATE_COMPRESS_TO_SENTENCES` and `STATE_HARD_KB` in `amh.conf`, deliberately **not** restated as
> numbers here — nothing checks this prose against the config, so a copied number drifts the first
> time a threshold moves. `scripts/ladder.sh` prints the caps whenever it reports the size.
>
> - Grow freely to the soft cap; **no trimming below it.** Above the hard cap the ladder fails.
> - When the guard warns, run **ONE deep pass to the floor** — never to just under the soft cap. The
>   floor counts sentences, so shaving words cannot satisfy it; fold whole completed stages. A
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

Harness AMH 9.1.0 (DB-073); upstream manifest scripts are immutable. **v1.9.0/vc21 is released**
(owner, 2026-08-18; #117 squash-merged, tag `v1.9.0` on `main`), and the owner's implicit ratify
closed the DB-064 judgement call. Parity checklist and parity gaps empty. Live ledger: `LEDGER_B.md`.

**Resuming cold?** **v1.9.1/vc22 is released** (owner, 2026-08-20; #119 squash-merged and tag
`v1.9.1` published from `main`). Both ephemeral device-round scripts are folded into
`DEVICE_TEST_SCRIPT.md` and deleted — **no ephemeral device script is outstanding.** Nothing is
half-done and no review is owed. Branch now carries unreleased **v1.9.2/vc23** for the AppOps
compatibility cleanup below. Do **not** re-open the closed force-stop investigation
(DB-051…DB-060). Scorecard.dev remains a run-once/local input, not a retained score or CI gate;
playbook 8 preserves the two resulting maintenance rails.

## Owner queue

> Protected by D-167. Test observable claims before restating them; preserve unresolved items.

1. DB-041…DB-043's unavailable-feature boundary still unverified (B1 BLOCKED three times): every
   device to hand reports Night Light/AOD available, and the owner has no Samsung. Needs hardware
   reporting them unavailable — park it until such a device exists.
2. **DB-074's Android 12/12L SSID check is unverified and needs hardware at API 31 or 32.** No JVM
   test can reach it (Robolectric runs the host `java.io`), so `:platform`'s lint gate is the only
   layer behind the fix. §8 step 24 carries the check; skip it on API 33+, where it proves nothing.
3. **DB-077/DB-078 want one device round on the unreleased vc23**, both reachable by hand and
   neither needing unusual hardware: §11 32a (set `stay_on_while_plugged_in` to `7`, confirm the
   notice, confirm an unrelated Apply preserves it, confirm the button writes `15`) and §11 32c's
   new button, which stays a SKIP unless a device reports an unrecognised daltonizer on its own.
Open questions: none. Owed reviews: the vc23 diff touches `RULE_FILES`
(`docs/RUNBOOK.md`, `scripts/verify.sh`, `scripts/guards/`, `scripts/tests/`) — the rule-review
protocol applies before merge, and the comment-budget re-baseline is the item to weigh.

Closed by the owner 2026-08-18: **DB-061** device-verified (location rule round-trips); the DB-064
legislation call **ratified** (implicitly — v1.9.0 was merged and released); the provenance-manifest
note (one record removed, one removed then restored, 14 no-coordinate records) **acknowledged**.

Incoming: force-stop location defect **closed as no app defect** (owner, 2026-08-17), cold-GNSS
warm-up → DB-059. Device rounds: 1.8.2-debug 49/5/2/3; 1.9.0 `7970765` 11/1/3, all owner-closed;
`fc35a6e`/`036ec77` → DB-054…DB-058; `12b5a21` → DB-060, verified fixed 2026-08-17. DB-065
confirmed on released 1.9.0 (0 → 7 via Tideo, 15 via Developer Options, off→on back to 7) and
**verified fixed on 1.9.1-debug vc22** the same day; that round also produced DB-071 and DB-072.

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
- **Never synthesise an unsupported value in a display key on a real device** (DB-071): no
  `settings put` of anything no AOSP path writes — daltonizer matrices, HDR format lists,
  `reduce_bright_colors_level`. Two owner devices have been left needing blind recovery. The
  unrepresentable-state paths (DB-045/DB-066) are unit-tested only; on-device they are an accepted
  unverified residual, observed read-only if hardware ever produces the state by itself.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-08-23 — **Post-v1.9.0 review: five findings fixed on the unreleased vc23 (DB-074…DB-078).**
  `:platform` had never been linted (`verify.sh` ran `:app:lintDebug` alone), which hid two `NewApi`
  errors on `readNBytes` that left the root and dumpsys SSID strategies silently dead on Android
  12/12L; the module is now gated and the read is hand-bounded. DB-065's dock bit had been cancelled
  by DB-068's diff-write on every device already carrying v1.9.0's mask of 7, because
  `readStayAwakePlugged()` could not distinguish it from this app's own — it is tri-state now, like
  HDR and daltonizer, with a notice instead of a false claim of dock coverage. Both preserved-state
  notices that sit beside a visible control gained a button that writes that one field directly,
  which is the only route to a value the control already shows. `hasUsageStatsAccess` lost an SDK
  branch built on a misread of the API-36 stubs, and `action-pins.sh` now mechanises the marker↔SHA
  rails that RUNBOOK playbook 8 had only in prose. The comment budget was re-baselined for the new
  declarations after the first repair took the overshoot from 38/16 lines to 14/8.
- 2026-08-22 — **Constitution rewritten for natural, direct prose.** `AGENTS.md` retains the
  existing AMH 9.1.0 rules, facts, citations and enforcement boundaries while replacing terse,
  emphatic phrasing with complete sentences and clearer organization. No policy changed.
- 2026-08-21 — **Agentic Maintenance Harness upgraded 5.2.0 → 9.1.0 (DB-073).** Copied the
  shipped scripts and manifest from the exact `amh-v9.1.0` tag; added sentence-based STATE and
  ledger limits with the byte limit retained as a backstop; updated current constitution,
  memory-tier and CI-triage prose; and reconciled the Claude/Codex adapters with the new secret,
  lifecycle, review and publication rails.
- 2026-08-20 — **v1.9.1 released; Dependabot #118 corrected; AppOps warning removed.** #119 was squash-merged and `v1.9.1`
  published from `main`, resolving its Owner-queue item. #118 was brought current with `main`; its
  stale checkout marker and three stale Node-runtime policy comments were corrected, and all action
  marker/SHA pairs were resolved against their upstream tags. The refreshed `build`, CodeQL,
  preflight, and complete four-stage F-Droid compatibility workflows passed on the PR.
  F-Droid vc21 exposed both remaining `unsafeCheckOpNoThrow` deprecation warnings (the platform
  monitor and onboarding's duplicate); one API-31-compatible helper now selects `checkOpNoThrow`
  on API 36+, and a forced release compilation is warning-free at both former call sites. Because
  this changes shipped code after the v1.9.1 tag, release preflight correctly required v1.9.2/vc23.
  The ephemeral Scorecard.dev score is not retained or rerun: its durable outputs remain playbook
  8's pinned-dependency and least-token-permission rails.
- 2026-08-18 — **RUNBOOK playbook 8 added: Dependabot upgrade / CI action bump.** Terse operational
  playbook (self-adaptation, not legislation) capturing the SHA-pin + Token-Permissions rails from
  the run-once/ephemeral Scorecard, the grouped-PR + gradle-security-only Dependabot policy, and
  DB-038's proven decay (marker↔SHA drift on lines with trailing prose; prose Dependabot won't edit;
  validate on the PR incl. fdroid-compat). Docs-only.
- 2026-08-18 — **v1.9.0 released** (#117 squash-merged, tag `v1.9.0`), and this branch bumped to
  **v1.9.1/vc22** with `changelogs/22.txt` for the fixes below. Tagging stays an owner step and
  waits on the owner. Both ephemeral round scripts (1.9.0, 1.9.1) folded into the standing
  `DEVICE_TEST_SCRIPT.md` and deleted: the fold added the location-button and rule round-trip checks
  (§8 24a/24b), stay-awake mask, HDR canonical-off/partial, read-only daltonizer, unsupported
  Night Light/AOD (§11 32a–32d), and the two Apply-path checks (§11 39b/39c).
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
