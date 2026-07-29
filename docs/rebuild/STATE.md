# STATE — project state & session memory

> **Length guard (read before editing — DA-004 hysteresis).** Grow freely to **14 KB**; no
> trimming below that line. When the ladder warns (> 14 KB), run ONE deep compression pass
> to **≤ 9 KB** — never trim to just under a threshold (micro-trims re-arm the warn a session
> later; the 9→14 KB band is the debounce). Fail > 16 KB. Compression means: collapse each
> completed *Active work* stage into one Changelog line, fold changelog clusters, move any
> durable gotcha into the ledger (permanent, append-only — never compressed), delete
> narrative/punch-list prose. The **Project**, **Current state**, and **Owner queue** sections
> must always survive compression (Owner queue items are the owner's to close — compress their
> prose, never drop an open item). The migration narrative is frozen in `../history/` — do not
> re-accumulate it here. (`scripts/ladder.sh` guard 1 machine-checks: warn > 14 KB, fail
> > 16 KB. Guard 1a (DA-014) machine-enforces the landing: a change that trims STATE from over
> the warn line but leaves it in the 9–14 KB band **fails** — a compression pass must reach the
> ≤ 9 KB floor, not just clear the warn.)

## Project

Native **Kotlin/Compose** Android rebuild of Tasker `Advanced_Auto_Brightness_V3.3`. Modules:
**`:domain`** (pure-JVM math/decision logic, golden-tested), **`:platform`** (Android adapters
behind small interfaces), **`:app`** (Compose M3 UI, DataStore `AabSettings`, FGS runtime, QS
tile, boot receiver). Privileges: **BASIC** `WRITE_SETTINGS` = full core pipeline; **ELEVATED**
`WRITE_SECURE_SETTINGS` (one-time `pm grant`) = super dimming + Privileged Display toggles.

## Current state

**Shipped: v1.8.1** (vc19, tagged). **Release pending: 1.8.2 / vc20** (patch), cut on the train
branch `claude/gradle-deprecation-fdroid-870gh3`: the AGP bump changes the shipped APK, so RUNBOOK §6
required it. Train tip `claude/badge-workflow-verify-77m62f` (DA-025) → `…-870gh3`, which carries
**AGP 8.7.3 → 8.13.2** (DA-026) + the **F-Droid compatibility CI** (DA-027/DA-028, green on real
infrastructure) and is open as **PR #96 → `main`** — the owner is keeping it as the base for the
next fix/feature. **Stacked on it: PR #99** (`claude/fold-prs-97-98-cdi4dp` → `…-870gh3`), which
folds the two concept PRs #97/#98 in as DA-029 (bounded profile import) + DA-030 (sticky-restart
gate); #97/#98 are superseded and close unmerged. So vc20 is **no longer a packaging-only release** —
it carries `app/` source and `changelogs/20.txt` says so; `domain/` and `platform/` are still
byte-identical to 1.8.1. #99 is **merged** into the train branch and PR #96's title/body have been
rewritten to the net `origin/main..HEAD` payload (DA-002 — the body becomes the squash commit), so
#96 squash-merging the whole train is the remaining step. At the tag: the one-shot DA-026
reproducibility check (RUNBOOK playbook 6) must not be
skipped — 1.8.2 is the first release under the new AGP — and the DA-024 store icon lands with it.
No other active work; no plan files. `PARITY_CHECKLIST.md` zero-`pending`; parity tests green;
TODO/FIXME 0; `parity_gaps.md` 0 open. Changes per `RUNBOOK.md`; deviations in
`DEVIATIONS_LEDGER.md` (live `_A.md`).

## Owner queue

> **Protected section (D-167).** Never delete this section or drop items during compression
> (guard 1b warns if the header vanishes). **Pending owner actions** = only-owner tasks; **Open
> questions** = owner-judgment forks (options + recommendation each, discipline 7); **Incoming
> findings** = owner on-device results. Items leave when done/answered/triaged (delete + record
> as a Changelog line or D-row). Final chat message restates this queue.

**Pending owner actions:**

1. Close **#97/#98** unmerged (superseded — their commits are contained in the merged #99), then
   squash-merge **PR #96** → `main` and **cut 1.8.2 / vc20** from the GitHub "Draft a new release"
   UI. The bump is already in the branch, so the release is tag + publish. (#99 is merged and #96's
   title/body already describe the whole train, DA-002.)
2. **At that tagged release (one-shot, DA-026):** after `release.yml` publishes, check F-Droid's
   build log for that versionCode reports `...successfully verified`. First release under AGP 8.13.2,
   so it is the first time F-Droid rebuilds our signed APK on the new toolchain — pre-verified in
   their own buildserver image, but never on the GitHub Actions runner. Full bullet + what a mismatch
   does (and does not) mean in RUNBOOK playbook 6; delete both once one release lands green.

**Open questions:** (none)

**Incoming findings:**

- 2026-07-24 — Owner confirmed the **server-side rails (DA-006)** are now enabled on GitHub
  (`main` branch protection + secret-scanning push protection) — closes the carried 1.8.0 item.

## Decided non-items (don't re-litigate without new evidence)

- **Repo/process (2026-06/07):** root `CHANGELOG.md`; speculative dep bumps (security advisories
  only); standalone doc-drift audit; action SHA-pinning / Gradle dep verification; widening
  build.yml to `claude/**` (D-161).
- **Triage #1 (2026-07-10, D-162) + YAML codification (2026-07-13):** glue-review checkbox
  output; ledger symlink/marker; session-start delta generator; platform contract tests (exist,
  D-136/D-148); tracking-id branch names; checkpoint manifest / glue-review YAML / per-playbook
  test matrices (Goodhart, re-litigates D-162).
- **Triage #2 (2026-07-20, DA-006):** verification manifests + machine session header; generated
  ledger index (grep IS the index); metrics dashboard; dep SHA-pinning playbook; scaffold
  CLI/profiles; ledger `Status:` retrofit; Owner-queue aging guard.
- **Triage #3 (2026-07-21, DA-010):** full verify-train script; PR-draft "whole train" guard;
  warm-up sentinel (Gradle's lock IS the sync).
- **Triage #4 (2026-07-21, DA-011):** prompt-cache doc-ordering (non-actionable agent-agnostic,
  contradicts grep-on-demand ledger, P6/D-176). Companion "unstick" idea ADOPTED → DA-012.
- **Triage #5 (2026-07-21, DA-013, all declined):** ledger-ID allocator script; RUNBOOK
  per-playbook split; XML tags in the constitution; STATE compression commit-or-ledger gate.
- **Triage #6 (2026-07-22, DA-015):** owner-decisions-per-change KPI (Goodharts against
  discipline 7; kept as P0 design orientation only); orphan-provenance `[cited]` extension.
- **Triage #7 (2026-07-24, DA-021) — "Claude 5 context engineering" blog rules:** no harness change,
  no CLAUDE.md rewrite. Its advice is already the design; the imperative bulk here is **policy rails**
  (git, secrets, ledger, ladder), not capability constraints. Auto-memory ≠ STATE.md; skills declined
  on D-176 agent-neutrality; RUNBOOK split stays declined (external content is not new evidence).
- **Privileged Display (D-150–D-152):** per-toggle orthogonal scheduling (D-151 pivot);
  persisted last-applied seed (revisit on real reports); QS tile / notification grayscale
  action; refresh-rate forcing / OEM alternate keys (D-048/D-149); manual Extra-Dim toggle
  (D-144/D-149).

## Changelog

- 2026-07-29 — DA-034 completed the manifest/privacy audit: permission request, disclosure, denial and revocation flows; explicit cloud/transfer allowlists for every personal-data category; stronger DUMP/elevated copy; and debug-only throwable logging with local crash diagnostics excluded from migration. Background location remains declared but has no second-stage in-app grant flow, now documented honestly.

One line per shipped change (newest first); detail in the D-rows and git history.

- 2026-07-29 — DA-033 reverted the unsanctioned DA-032 repository/container bootstrap changes;
  the requested JDK 21 setup remains chat-provided cloud configuration rather than repo policy.
- 2026-07-29 — DA-032 added a standard-container bootstrap; reverted by DA-033 because that
  repository-level policy change was outside the requested scope.
- 2026-07-29 — DA-031 privileged-command audit and hardening: traced every root/Shizuku/shell
  argument to its trusted source; replaced generic AIDL exec and caller-selected grant package with
  fixed typed operations; bounded process time/output/error streams; added cleanup, disconnect,
  exit-code, and non-sensitive error handling; documented the remaining device-verification limits.

- 2026-07-29 — Completed the Android component audit segment: manifest-wide activity/service/
  receiver/provider matrix, action/extra-to-side-effect traces, export/permission/code gates,
  replay/malformed-profile/FGS analysis, and explicit classification of enabled external control's
  ambient local authority as a documented product decision rather than an automatic defect.
- 2026-07-29 — Added the pre-implementation security audit model: protected display/configuration
  assets, Android IPC/SAF/network/root/Shizuku trust boundaries, attacker and lifecycle-failure
  classes, and explicit safety, consent, restoration, resource, and data-minimization invariants.
- 2026-07-28 — **DA-029 + DA-030** (folded into the 1.8.2/vc20 train): profile import is now a
  bounded stream (256 KiB cap, strict UTF-8, provider-declared size demoted to a hint) with typed
  `TooLarge`/`ReadFailure` outcomes surfaced in the Profiles UI; and a null-intent `START_STICKY`
  restart no longer starts the runtime before DataStore confirms the persisted opt-in — the
  notification is posted first for the FGS deadline, the gate fails closed, and a newer command or
  `onDestroy` supersedes a pending decision. Explicit starts keep their synchronous path.
- 2026-07-28 — **1.8.2 / vc20 cut** (patch): release-preflight correctly classified the AGP bump as
  shipping app code, so RUNBOOK §6 required the bump — taken rather than weakening the gate.
- 2026-07-28 — **DA-026 → DA-028**: **AGP 8.7.3 → 8.13.2**, verified in F-Droid's own buildserver
  image (at 8.7.3 the rig reproduces both the published log and the v1.8.1 APK content; at 8.13.2
  the warnings vanish and two environments emit an identical whole-file SHA-256). Then the
  **F-Droid compatibility CI** (DA-027) and its adversarial fixes (DA-028) — green on real
  infrastructure at PR #96, but only after the pass caught that `upload-artifact`'s
  least-common-ancestor rooting made stages 3+4 unpassable and that `paths:` + `tags:` are ANDed,
  so the advertised release backstop never fired. Limits in `FDROID_VALIDATION.md`.
- 2026-07-28 — **DA-024 + DA-025 + README install surface**: F-Droid store icon PNG (the listing
  can't rasterize our adaptive-icon XML; lands at the next tagged release), split
  GitHub/F-Droid download badges, and the "Get it on F-Droid" badge. **Owner-caught correction:**
  the two download sources share one signing key — reproducible-build mode means F-Droid
  redistributes *our* signed APK, so they are interchangeable.
- 2026-07-23/25 — **DA-016, DA-018, DA-021, DA-023, DA-020→DA-022**: curve-wizard top-K bubble-up
  mis-port fixed (`wizard.csv` regenerated); the **1.8.1/vc19** Resume-context field bug
  (`ACTION_RESUME_CONTEXT` runs a genuine `evaluate(RESUME)`; resolver falls back to the persisted
  `%AAB_ProfileUser`); release-preflight freed from the GitHub API; triage #7 (no change); Ko-fi as
  a repo-side surface only, the in-app card owner-reversed pre-release.
- 2026-07-10..24 — **D-161…D-176 + DA-001…DA-017**: repo hardening (ladder guards + fixture suite,
  deny rules + command guard, guarded Owner queue, secret hygiene D-175/DA-006–DA-009); triages
  #1–#6; agent-agnostic harness (D-176); branch-train (DA-002); STATE hysteresis (DA-004/DA-014);
  the DA-017 CI hang (a test blocked forever on the runner's password-prompting `su`); F-Droid
  inclusion (`fdroiddata!41202`); final adversarial audit; force dark; harness prompt v1.0→v1.8.
- 2026-06-23..07-09 — **v1.0.0 Tasker→Kotlin rebuild** (Gate 3; frozen in `../history/`) →
  **1.7.0/vc17** → 1.8.0 close-out: D-096–D-160 — SDK 36/JDK 21/CodeQL, release CI, glue
  review, F-backlog, **Privileged Display** (D-151 pivot), intent control, a11y + crash-log,
  IME/RESUME.
