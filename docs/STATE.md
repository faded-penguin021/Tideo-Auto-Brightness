# STATE — project state & session memory

> **Length guard (DA-004 hysteresis — read before editing).** The thresholds are
> `STATE_WARN_KB`, `STATE_COMPRESS_TO_KB` and `STATE_HARD_KB` in `amh.conf`, named here and
> deliberately **not** restated as numbers: nothing checks this prose against the config, so a
> copied number drifts silently the first time a threshold moves. `scripts/ladder.sh` names the
> soft and hard caps whenever it reports the size, and the floor whenever it warns, fails, or
> confirms a completed landing. Grow freely to the soft cap; no
> trimming below it. When the guard warns, run ONE deep pass to the floor — never trim to just
> under the soft cap, because a micro-trim re-arms the warning a session later and the wide band
> IS the debounce. The floor is a **ceiling, not a target**: if the pass lands short, fold MORE
> completed stages rather than micro-trimming toward it. Fail above the hard cap. Compression
> means: collapse each completed stage into one Changelog line, fold changelog clusters, move
> durable gotchas to the append-only ledger, delete narrative prose.
>
> **Project**, **Current state**, **Decided non-items** and **Changelog** must always survive —
> those four are `STATE_REQUIRED_SECTIONS` and the guard FAILS on a missing or empty one.
> **Owner queue** is protected separately at WARN level: never delete it and never silently drop
> items during compression — they are the owner's to close, so compress their prose rather than
> dropping an open item. Separately, **no `##` heading may appear twice** — that check is asked of
> every level-2 heading in the document, not just the configured four, because a scripted edit
> that splices the file into itself duplicates whatever it happens to duplicate. Two copies of a
> section are two answers to the same question, and nothing else here can see one: the caps
> measure bytes and the landing check measures shrink, both of which a duplicate satisfies.
>
> The ladder also checks WHERE a pass lands, and the rule is sharper than it reads. **Any** edit
> that takes the file from above the soft cap to at or below it must reach the floor, however
> small the edit was: a five-byte typo fix at 14340 bytes crosses the cap and FAILS unless it
> also lands under the floor. The `STATE_EDIT_DELTA_BYTES` distinction between a compression
> pass and an ordinary edit applies only while the file is **still above** the cap. Two traps
> follow: never pad the file back up to escape that failure, and never trim a file that is
> already under the cap — a pass that starts below it is invisible to the landing check, so
> nothing will tell you it stopped short. The structure checks above still run at every size; it
> is only the size guard's landing half that goes quiet. That silence is the absence of a check,
> not a verdict that the edit was right — and **do not reach for a threshold to cover it.** It is
> the SHRINK that is measured, never the band, and a check that read any large shrink as a
> compression pass would fail a session for deleting one resolved Owner-queue item from a healthy
> file, leaving padding the file back as the only way to pass.
>
> **That list is the whole of what these two functions check** — this file existing at all, sizes,
> the required sections and their bodies, repeated headings, the Owner-queue heading, the landing
> check, plus a warning if `STATE_EDIT_DELTA_BYTES` is malformed. It is a claim about
> `guard_state_size` and `guard_state_structure` in `scripts/ladder.sh`, a file that upgrades
> independently of this one, so treat those two functions as the authority: if a later harness
> version adds a rung, this sentence is what goes stale, and nothing checks it against the script.
> Everything else here — what to fold, what to move to the ledger, whether to compress at all —
> is prose you are asked to keep, and no guard will catch you breaking it.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure-JVM
math/decisions, `:platform` Android adapters, `:app` Compose/DataStore/FGS UI/runtime. BASIC
`WRITE_SETTINGS` runs the core pipeline; ELEVATED `WRITE_SECURE_SETTINGS` adds super dimming and
Privileged Display.

## Current state

**Harness: AMH 5.2.0** (DB-027; converged at 3.0.0, DB-014…DB-016). **The five scripts in
`scripts/MANIFEST.sha256` are upstream's byte-for-byte and hash-checked every run — never edit
one;** changes go to `amh.conf`, `scripts/guards/*.sh` or `scripts/verify.sh`. Constitution:
`AGENTS.md` (rows before 2026-08-03 cite the old name). `docs/HARNESS_LOCAL.md` records every
local deviation; the next upgrade reads it first.

**Shipped: v1.8.2 (vc20)**, tagged, `main` carrying vc20, **F-Droid reproducible-build verified**
(owner, 2026-08-13 — the first AGP 8.13.2 release built clean, no complaint about the bump, so
DA-026's fallback was never needed). No plan files; parity checklist zero-pending; tests green;
TODO/FIXME and parity gaps zero. Live ledger: `LEDGER_B.md`.

## Owner queue

> **Protected (D-167).** Owner items survive compression and leave only when done, answered or
> triaged. Test each before restating it; the final chat restates it.

**Pending owner actions:**

1. Device-verify the Tasker-parity train on `claude/tasker-tideo-comparison-gdaxux`: **§11.39a**
   (Privileged Display shows the device, DB-034), **§13.44a** (drop reasons at debug level 8,
   DB-035) and **§5.14a** (every panic entry point vibrates, exactly once, DB-037). **Not observable
   from a session** — no emulator/KVM.

**Open questions:** (none)

**Owed reviews:** (none)

**Incoming findings:**

- 2026-08-02 — **Owner device pass, 1.8.2-debug: 49 PASS / 5 FAIL / 2 BLOCKED / 3 SKIPPED.** Two
  real defects fixed (DB-011, DB-012); three FAILs were script defects. Section J's `bmgr restore`
  omitted its package argument and irreversibly overwrote settings across unrelated apps on the
  owner's phone — retired at their instruction (DB-013).
- **Still open (DB-012):** `PrivilegeManager` is per-`AppModule` at ~10 call sites, so the "shared"
  tier cache is shared within one instance only. A process-wide singleton is the real fix.
- 2026-07-30/24 — Owner confirmed no open Dependabot alerts (DA-041); `main` protection and
  secret-scanning push protection on (DA-006).

## Decided non-items

- **Repo/process:** root changelog; speculative dependency bumps; standalone drift audit; Gradle
  dependency verification; widening build CI to session branches (D-161). **Action SHA-pinning left
  this list on 2026-08-13 (DB-038, owner decision):** the 2026-06-29 decline assumed pins nothing
  refreshes, and enabling Dependabot version updates for github-actions supplies the refresh path,
  so the premise changed rather than the argument being re-litigated. Gradle dependency verification
  stays declined; DA-040's wrapper digest is a narrower executable-integrity finding — now enforced
  in CI by `wrapper-validation` — and reopens neither declined program.
- **Triage #1–#6 (D-162, DA-006/010/011/013/015):** glue checkbox output; ledger
  index/symlink/status retrofit/ID allocator; session delta/header/manifest; per-playbook matrices
  or RUNBOOK split; dashboard/KPIs/aging guard; dependency-pin playbook; scaffold CLI/profiles;
  train verifier, warm-up sentinel, PR-body guard; auto-memory rewrite; orphan-provenance.
- **Triage #7 (DA-021):** no harness rewrite from the external context-engineering blog; the rails
  are intentional and agent-neutral. Companion recovery stop became DA-012.
- **Privileged Display (D-150–152):** per-toggle scheduling; persisted last-applied seed absent
  real reports; QS/notification grayscale action; refresh-rate/OEM keys; manual Extra-Dim toggle.
- **On-device backup/restore verification (owner, 2026-08-02, DB-013):** not to be tested again.
  The script's whole-device `bmgr restore` damaged unrelated apps; the owner declined a re-run and
  that decline is binding. `SettingsBackupSanitizer` stays unit-tested and its allowlist
  inspectable; that `onRestoreFinished` is actually invoked is an **accepted unverified residual**
  for 1.8.2. Do not re-add the steps without new evidence (e.g. a spare device).

## Changelog

Newest first; older clusters fold to one line. The cited ledger rows are the record.
- 2026-08-13 — **Supply-chain pass against OpenSSF Scorecard v5.5.0; 1.8.2 → 1.9.0 (DB-038).** All
  39 action call sites pinned to the commit their `@vN` already resolved to, annotated with the
  semver — immutability, not an upgrade — with github-actions version updates enabled in Dependabot
  so the pins cannot rot (gradle stays security-only, D-135 unchanged). That pairing is what
  reopened the 2026-06-29 SHA-pinning decline: changed premise, not re-argument. Top-level
  `contents: write` and the two writes in `redirect-external-prs.yml` moved to job scope.
  `gradle/actions/wrapper-validation` added to all five workflows that execute the checked-in
  wrapper jar — release and signing paths too — as the control standing in for a Binary-Artifacts
  finding we will not "fix": the jar has to stay. `SECURITY.md` links the advisory form directly.
  Local Scorecard 6.6 → 9.0 (Pinned-Dependencies, Token-Permissions 0→10; Security-Policy 4→10).
  **Caveat:** `--repo` mode is unusable here — the proxy blocks GraphQL, so every check aborts at
  client setup — so host-backed checks rest on the owner's authenticated scan, not this session's.
  Declined with reasons: fuzzing, CII badge, Scorecard CI, SLSA provenance (real value, deferred as
  its own design). **Owner settings:** Dependabot **security** updates was OFF, now on — D-135
  assumes it, so gradle at limit 0 had no mechanism behind it; the rest were already correct.
- 2026-08-13 — **Panic confirms itself however it was triggered (DB-037).** `vibrateSos()` moved from
  the two gesture collectors into `panicAndStop()`, the path the gesture, the control intent and the
  notification's Reset button all share — the latter two reset the device silently before.
  Glue review returned 10 findings (1 blocking); the fixes reshaped it: the buzz now follows
  `emergencyStop()` rather than preceding it (a sibling DISABLE could cancel the recovery and leave
  the user confirmed-but-unrestored), `panicInFlight` stops a double-tapped Reset running the whole
  recovery twice, and the counter moved inside the vibrate success path — it previously stayed green
  with the `vibrate()` call deleted. `ACTION_PANIC` gains its first tests. Owner step: §5.14a.
- 2026-08-13 — **Dropped control commands explain themselves (DB-035).** Four `ControlReceiver`
  drops — gate off, `LOAD_PROFILE` missing/unknown name, `RESUME` while the master switch is off —
  flash the reason at debug level 8 via the existing `ToastDebugSink`; `applyProfile` returns Boolean
  so an unresolved name is distinguishable. Silent at every other level, so the default config keeps
  D-157's pre-gate invariant; `SECURITY_REVIEW.md` row amended to the qualified form rather than left
  overclaiming. Unknown-action and admission-gate drops stay silent (unbounded per broadcast).
  Owner step: §13.44a. Also fixed a **pre-existing** flaky test found by the red ladder (DB-036):
  `DraftSettingsViewModelTest` awaited the DataStore then asserted on the VM's `dirty`, which its
  collector updates strictly later — failed ~2 of 3 runs under load, on a clean tree too.
  Glue review raised 9 findings; the 4 should-fix are fixed (level checked before the work, hostile
  `name` clamped, two docs corrected for overclaiming). **Left open as nits:** no test drives
  `onReceive` itself, `SettingsViewModel`/`ProfilesContextsScreen` still ignore `applyProfile`'s new
  Boolean and toast success unconditionally, and `awaitVm` makes the issue-#110 fixed-point assertion
  eventually-consistent (catches a permanent-dirty regression, not a slow-converging one).
- 2026-08-12 — **Privileged Display shows the device, not the profile (DB-034).** The screen's draft
  is seeded from a `SecureDisplayController` read-back on open and every resume — the seven `read*`
  methods had no production caller, so an externally-flipped toggle left the UI asserting a value
  the device did not hold and the only-on-change diff never noticed. Skipped while the draft is
  dirty; temperature excluded while circadian tracking owns that key. Coordinator seeding
  deliberately unchanged (its "seed must not write" invariant holds). Owner step: §11.39a.
- 2026-08-11 — **Comment consolidation + the rail that holds it (DB-028…DB-033).** Kotlin comments
  18.7% → 8.4% (7620 → 3015 lines, 215 files), each proved comment-only against the branch point;
  prose moved to the `.md` tier with `D-NNN` pointers in code, and Conventions no longer says "match
  its comment density". Fifth repo-local guard `comment-budget.sh` — block cap, module budgets,
  Tasker-provenance manifest, fail-closed, plus a `--hook` mode the Claude adapter runs on every
  `.kt` write (Codex has none; the adapter table says so). Fixtures 24 → 74, self-checking against
  the sentence stating the count. **Three review passes, each finding the enforcement weaker than
  its own prose claimed** — the six rows are the record.
- 2026-06-23..08-10 — **v1.0.0 → 1.8.2, then the AMH convergence (D-096…D-176, DA-001…DA-044,
  DB-001…DB-027).** The rebuild and its release/glue gates; the ladder/ledger/state/secret/git
  rails, branch-train and F-Droid inclusion; triages #1–#7; the hardening train; Tasker parity
  fixes and an adversarial security round (five findings, all real and fixed, three more the review
  missed). Then AMH 3.0.0 at the `full` profile replaced the harness this repo originated, and four
  upgrades carried it to 5.2.0. Two lessons cost real work: a 42% constitution cut landed unreviewed
  (DB-018), and the whole-word matcher stopped resolving `D-042c` citations, answered by deleting
  three `[cited]` markers (DB-022).
