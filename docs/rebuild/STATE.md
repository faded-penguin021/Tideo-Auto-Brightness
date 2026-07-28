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

**Shipped: v1.8.1** (vc19, tagged; v1.8.0/vc18 before it). **No release pending** — no version bump on
this branch, so vc20 stays unassigned for the next real release. The branch train tip is
`claude/badge-workflow-verify-77m62f` (DA-025 badges); this session's branch is cut from it and adds
the **AGP 8.7.3 → 8.13.2** bump (DA-026) — build tooling only, no `app/`/`domain/`/`platform/` source
change, but it *does* change the next release's APK (`classes*.dex` + baseline profile), which makes
the DA-026 release-time reproducibility check in RUNBOOK playbook 6 the one thing that must not be
skipped at the next tag. The DA-024 store icon likewise reaches F-Droid only at that **next tagged
release**. No other active work; no plan files. `PARITY_CHECKLIST.md` zero-`pending`; parity
tests green; TODO/FIXME 0; `parity_gaps.md` 0 open. Changes per `RUNBOOK.md`; deviations in
`DEVIATIONS_LEDGER.md` (live `_A.md`).

## Owner queue

> **Protected section (D-167).** Never delete this section or drop items during compression
> (guard 1b warns if the header vanishes). **Pending owner actions** = only-owner tasks; **Open
> questions** = owner-judgment forks (options + recommendation each, discipline 7); **Incoming
> findings** = owner on-device results. Items leave when done/answered/triaged (delete + record
> as a Changelog line or D-row). Final chat message restates this queue.

**Pending owner actions:**

1. **At the next tagged release (one-shot, DA-026):** after `release.yml` publishes, check F-Droid's
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
  CLI/profiles; ledger `Status:` retrofit; Owner-queue aging guard. (Secret-pattern decline
  owner-reopened → guard 9, DA-008.)
- **Triage #3 (2026-07-21, DA-010):** full verify-train script; PR-draft "whole train" guard;
  warm-up sentinel (Gradle's lock IS the sync).
- **Triage #4 (2026-07-21, DA-011):** prompt-cache doc-ordering (non-actionable agent-agnostic,
  contradicts grep-on-demand ledger, P6/D-176). Companion "unstick" idea ADOPTED → DA-012.
- **Triage #5 (2026-07-21, DA-013, all declined):** ledger-ID allocator script; RUNBOOK
  per-playbook split; XML tags in the constitution; STATE compression commit-or-ledger gate.
- **Triage #6 (2026-07-22, DA-015):** owner-decisions-per-change KPI (Goodharts against
  discipline 7; kept as P0 design orientation only); orphan-provenance `[cited]` extension.
- **Triage #7 (2026-07-24, DA-021) — "Claude 5 context engineering" blog rules:** no harness
  change, no CLAUDE.md rewrite. Progressive disclosure / interface-over-examples / principle-based
  guidance are already the design; the imperative bulk here is **policy rails** (git, secrets,
  ledger, ladder), not the capability constraints that post says to delete. Auto-memory ≠ STATE.md
  (shared, guard-checked artifact). Skills declined on D-176 agent-neutrality (`.claude/` forks the
  constitution per-agent). RUNBOOK per-playbook split stays declined (DA-013) — external content is
  not new evidence. Carried awareness, no action: Opus 5 over-verifies when instructed to verify
  (glue-review/rule-review stay — real catch history) and delegates to subagents more readily.
- **Privileged Display (D-150–D-152):** per-toggle orthogonal scheduling (D-151 pivot);
  persisted last-applied seed (revisit on real reports); QS tile / notification grayscale
  action; refresh-rate forcing / OEM alternate keys (D-048/D-149); manual Extra-Dim toggle
  (D-144/D-149).

## Changelog

One line per shipped change (newest first); detail in the D-rows and git history.

- 2026-07-28 — **DA-026** (owner-asked, from the vc19 F-Droid build log): **AGP 8.7.3 → 8.13.2**.
  The log's "incompatible with Gradle 9.0" notice is three deprecations *inside AGP*, none from our
  scripts, and F-Droid takes its Gradle version from our own `distributionUrl` — so nothing forced the
  date; the bump was taken early on purpose, since a toolchain change cashes its risk at the next
  release. Verified in F-Droid's own `fdroidserver:buildserver` image via its real
  `gradlew-fdroid assembleRelease` entry point (Gradle seed checksum-matched to gradle.org *and* the
  F-Droid transparency log): at 8.7.3 the rig reproduces the pasted log and the published v1.8.1 APK
  content (119/119 CRCs); at 8.13.2 both warning classes vanish, `lintVitalRelease` passes, and the
  F-Droid image and dev env emit an **identical whole-file SHA-256** APK. Delta vs 1.8.1 is 4 entries
  (2 dex + baseline profile). Release-time re-verify obligation in RUNBOOK playbook 6 + Owner queue.
- 2026-07-28 — **DA-025** (owner-started, verified + finished this session): README badge row now
  distinguishes the two download sources — `Downloads (GitHub)` (live: 326) and a new
  `Downloads (F-Droid)` (shields `dynamic/json` over the `kitswas/fdroid-metrics-dashboard`
  per-package JSON), each linked to its own source. The F-Droid badge currently renders
  `resource not found`: the URL template is correct (proved by substituting a published package,
  which returns a real count) — the app only landed on F-Droid today, so the dashboard's daily
  cronjob has no data file for `com.tideo.autobrightness` yet. It self-heals with no repo change.
- 2026-07-28 — README install surface: the official **"Get it on F-Droid" badge**
  (`f-droid.org/badge/get-it-on.png` — the owner-specified English asset; a shields.io variant was
  declined, and the `.svg` at the same path is the alternate) linking to
  `f-droid.org/packages/com.tideo.autobrightness/`, plus an Install step naming F-Droid as a source
  alongside Releases. Both URLs verified live (HTTP 200). **Correction in the same session
  (owner-caught):** the step first claimed the two sources use different signing keys — false. The
  fdroiddata recipe is reproducible-build mode (`Binaries:` + `AllowedAPKSigningKeys`
  `3d2d9dd1…`, verified against the live YAML, the D-137 submission landing), so F-Droid
  redistributes *our* signed APK — the sources are interchangeable, no uninstall to switch.
- 2026-07-28 — **DA-024** (owner-reported, store screenshot): the F-Droid listing showed the generic
  placeholder icon because the app ships only an adaptive-icon XML and F-Droid can't rasterize one.
  Added `fastlane/metadata/android/en-US/images/icon.png` (512×512) rendered from the new in-repo
  source `docs/rebuild/design/store_icon.svg`; re-render lockstep is prose in RUNBOOK playbook 6
  (no guard — DA-015 incident-only bar). Visible at the next tagged release, not retroactively.
- 2026-07-25 — **DA-023** (PR #93 CI): release-preflight no longer calls the GitHub CLI/API to read PR title/commits/files; it derives the same data from the checked-out full-history repository (`github.event.pull_request.*` SHAs + local `git log`/`git diff`) so docs-only PRs are not blocked by repeated GitHub API/internal-server failures.
- 2026-07-24 — **DA-021** (triage #7, owner-asked): assessed the "Claude 5 context engineering"
  rules against this harness — verdict no change (detail in the decided non-item above).
- 2026-07-24 — **DA-020 → DA-022** (owner-requested, then owner-reversed pre-release): Ko-fi funding
  is a **repo-side surface only** — `.github/FUNDING.yml` (`ko_fi: fadedpenguin021`, drives the repo
  Sponsor button; GitHub reads the file, not a settings toggle) plus a shields.io badge in the README
  badge row. The About-screen support card, its strings/test, the 1.8.2/vc20 bump and `20.txt` were
  all reverted to match `main`; no in-app donation link and no F-Droid `Donate:` field ship.
- 2026-07-24 — **DA-018** (owner-reported field bug): "Resume context automation" only republished
  (`ContextEngine.reevaluate` + reapply) — never ran the resolver — so a matching rule didn't apply and
  the active-profile label flipped to the hardcoded "Default" while the write-through settings kept the
  loaded profile (indicator/settings diverged). Fix: a dedicated `ACTION_RESUME_CONTEXT` verb runs a
  genuine `evaluate(RESUME)` then Set Initial Brightness (Tasker `_ContextResume` flow), and the resolver
  fallback is now the persisted `%AAB_ProfileUser` = last manually-loaded profile (`ContextBaseline` v2,
  `userProfileName`). Cut as **1.8.1 / vc19** (patch, on `main` after 1.8.0 shipped); debug APK + test
  script sent to owner; awaiting owner on-device verify + release.
- 2026-07-24 — **CI hang root-caused + fixed (DA-017)** (PR #91, two red runs):
  `ForceDarkControllerTest` spawned the runner's real `su`, whose password prompt blocked
  `rootExec`'s unbounded stdout read forever (named by run 2's jstack step; run 1's
  config-cache-store theory disproven — flag kept as a cost skip). Fix in production code:
  stdin closed at spawn + 15 s bounded wait with kill. CI hardening kept:
  `--no-configuration-cache`, `~/.robolectric` cached, 20-min ladder step cap + jstack dump.
- 2026-07-24 — **F-Droid inclusion complete; release PR opened:** `fdroiddata!41202` merged
  (detail in Owner queue 1); release **PR #91** opened and retargeted to `main` with the
  whole-train title/body; train tip advanced to this session's branch.
- 2026-07-23 — **DA-016** (owner-reported field bug): curve-wizard Stage 1 top-K bubble-up
  mis-ported (one swap per insert) → shortlist collapsed; ported the reference full pass,
  regenerated `wizard.csv` (10/12 cases shifted), added sorted-descending test.
- 2026-07-22 — **DA-015** (cross-model triage): differential sweep parity test (seeded, 5×4000);
  ladder guard 11 falsifiable doc-facts; prompt v1.7→v1.8 (P0 thesis; P19/P20 back-port).
  Also harness v1.6 export polish; runtime-Shizuku doc count corrected one → two (D-172).
- 2026-07-21 — **DA-011…DA-014:** triages #4/#5 (declines above); bounded-recovery stop
  condition (DA-012, discipline 6, harness v1.4); STATE compression-landing guard 1a (DA-014,
  harness v1.5).
- 2026-07-10..21 — **D-161…D-176 + DA-001…DA-010:** repo hardening (`ladder.sh` guards + test
  suite, CI-run, deny rules + command guard, guarded Owner queue, secret hygiene D-175/
  DA-006–DA-009); triages #1–#3; final adversarial audit; parity/relabel fixes; force dark;
  agent-agnostic harness (D-176); branch-train (DA-002); STATE hysteresis (DA-004); harness
  prompt v1.0→v1.3. Detail in each row.
- 2026-06-23..07-09 — **v1.0.0 Tasker→Kotlin rebuild** (Gate 3; frozen in `../history/`) →
  **1.7.0/vc17** → 1.8.0 close-out: D-096–D-160 — SDK 36/JDK 21/CodeQL, release CI, glue
  review, F-backlog, **Privileged Display** (D-151 pivot), intent control, a11y + crash-log,
  IME/RESUME.
