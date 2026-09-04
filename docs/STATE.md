# STATE — project state & session memory

> **Length guard (DA-004).** Thresholds are in `amh.conf`; the rules for compressing this file are
> `docs/RUNBOOK.md` → **Working-memory compression**, and they bind whether or not you follow this
> pointer. Fold completed narrative when its stage completes; retain only current state, unresolved
> owner items, immediate operational gotchas and concise changelog pointers.
>
> **Tree-relative.** That same section says what may be in `Current state` at all — the Changelog
> and ledger pointers are historical storage and are exempt: it records what stays true of the
> checked-out tree, never world-controlled status (merged, tagged, released, PR and CI state,
> deployments, remote branches, forge settings) as current truth. Point at a live probe instead of
> storing its last answer, route an unresolved external action to the Owner queue, and scope a
> retained past observation to when it was observed. Prose-only — no guard judges it.

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

**Resuming cold?** Release standing is NOT recorded here — the session banner computes it live via
`scripts/session-facts.sh` (DC-030), settled by hand with
`git ls-remote --tags --refs origin 'refs/tags/v*'`. Scripts and binding prose are both AMH 14.0.0
now that the owed seed prose has landed, so `AMH_PROSE_VERSION` equals `AMH_VERSION` and
`doc-facts.sh` is quiet on the pair (DC-031, DC-036). Unguarded still: nothing stops a
later session re-caching a release number.

This branch carries Graph Metrics (DC-001), the LEDGER_C rollover, the executed #126/#127
override-attribution work (DC-002…DC-028) and the harness units DC-029…DC-035; its plan was deleted
at completion by owner decision, as the lifecycle requires. Device rounds on 1.10.0-debug vc24 are
**all closed** — §2 10b/10c/10d passed 2026-08-30..31, the 0–4095 scale sits BELOW the app-facing
Settings API and the owner ruled no fix, freezing the conversion path as built; readings are in the
rows, checks in `DEVICE_TEST_SCRIPT.md` §2, and a later build owes its own run (DC-011…DC-013,
DC-025…DC-028, DB-083). No round script is alive (RUNBOOK §6, DB-010), the force-stop investigation
stays closed (DB-051…DB-060), and Scorecard.dev is a run-once local input, not a score or CI gate.

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
   phone that old (DB-074, §8 24); the unrecognised-colour-mode button needs a phone reporting a mode
   Android does not know, and a fake value must never be written to force one (§11 32c, DB-071,
   DB-078); Night Light / always-on failing safely needs a Samsung, every phone to hand reporting
   them available (DB-041…DB-043).
2. **Nothing to do — issues #123, #126 and #127 get no reply.** Owner's decision (2026-08-24 for
   #123, carried forward by the plan); nothing was posted, and do not comment without the owner
   saying so first (DB-082).
3. **Backlog, owner-approved 2026-08-30 but NOT for this train — give the Graph Metrics wiring real
   tests.** Nothing covers `ChartCanvas` calling the sink, the sink being null below level 7, or the
   signature dedupe suppressing a repeat draw, and the one test that looks like it does passes
   unchanged on `b462e56` — which is why the owner's device sighting is the feature's only evidence.
   Contained Compose work, its own unit (DC-001).
4. **Nothing to do — set `JAVA_HOME` to an x86_64 JDK before the ladder on a Windows-on-ARM host
   (2026-09-02, DC-033).** Not a preference: an aarch64 JVM fails 120 of 145 `:platform` Robolectric
   tests for a native conscrypt does not publish, an x86_64 JVM under emulation is 145/145, and
   Temurin 21 x64 is CI's exact vendor, version and architecture. Two host settings ride with it,
   both now on: Developer Mode plus `MSYS=winsymlinks:nativestrict`, without which `ln -s` silently
   writes a copy, and a real `python3` ahead of the Microsoft Store alias, which otherwise answers
   `command -v` and exits 0 without running Python.
5. **Nothing to do — the DA-005 review owed on `845bb75` is discharged, and it was NOT clean
   (2026-09-04, DC-035).** A fresh-context Codex reviewer over `4e22273..HEAD` confirmed what the
   item asked — `Upgrading` reads forward from `AMH_PROSE_VERSION`, `doc-facts.sh` warns at 2 and
   fails at 1 with fixtures pinning both — and caught the contradiction nobody had: `HARNESS_LOCAL.md`
   still claimed all eight repo-local guards fail closed after `845bb75` made that false, fixed here
   with two lower findings of this session's own. The PR #128 hold is lifted on this ground alone.

Open questions:

- **[2026-08-31] The rename half of the deferred cleanup — take it, or drop it with the other
  half?** The no-fix ruling already declined (i), moving `deviceMax` to `context.resources`, as
  hardening for hardware nobody has (DC-019, DC-026). That leaves (ii): rename `deviceMax`/
  `requestedRaw`/`acknowledgedRaw` to `settingsApiMax`/`requestedSettingValue`/
  `readBackSettingValue`, with the Live Debug labels becoming "Settings API max" and "Settings value
  requested" — no behaviour change, just names that say what 10d proved they are, app-facing
  Settings API values rather than hardware, which is the misreading that produced DC-014 (DC-023).
  **Recommendation: take it, and the hold is discharged** — the only reason to wait was that §2 10b
  cited the present card labels verbatim, and 10b passed (DC-027).

**Decided (owner).** This train ships as a **minor**, `1.10.0` / vc24 (2026-08-30,
`app/build.gradle.kts`). Everything raised through 2026-09-02 is closed in DB-054…DB-085 and
DC-001…DC-035.

## Decided non-items

- Repo/process declines remain: root changelog, speculative dependency bumps, standalone drift
  audit, Gradle dependency verification, wider session-branch CI, the D-162/DA-021 triage sets (SHA
  pinning left this list when Dependabot supplied a refresh path, DB-038).
- Still declined: the superseded Privileged Display schedule and a persisted seed without real
  reports (D-150–152), a grayscale quick action, refresh-rate/OEM keys, manual Extra Dim, panic
  re-firing after teardown, §11.39a C1/C2 as wontfix, and repeating the destructive `bmgr restore`
  verification (DB-013). The test-only `ContextsContent` wrapper stays test-only, since migrating its
  13 sites buys nothing and risks accessibility coverage; the rest of the triage is in
  `docs/plans/REVIEW_TRIAGE_1.9.0.md` (`WAIT-MINOR-003`).
- **Never synthesise unsupported display values on a device** (DB-071); use a real settings UI.
  DB-077 is exempt because mask 7 was written by Tideo v1.9.0 and §11 32a is device-verified.
- Rejected by the #126/#127 plan, not to be reintroduced: keying wake behaviour on
  `ACTION_USER_PRESENT`/unlock (owner, 2026-08-30), a larger fixed or blanket settle window, wake
  baseline adoption, a recent-write token set (D-034/D-051(d)), auto-learning the device maximum —
  and, by the owner's 2026-08-31 no-fix ruling, resolving `deviceMax` through `context.resources`
  (DC-019, DC-026).

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-09-04 — **The owed AMH seed prose for 9.2.0…14.0.0 landed; both version keys are 14.0.0
  (DC-036).** RUNBOOK gained the **Working-memory compression** section 9.2.0 created and this tree
  had never had — carrying 11.0.0's fold-by-lifecycle rule, 12.0.0's ban on counter-only rewrites,
  13.0.0's threshold vocabulary and 14.0.0's tree-relative content rule — and `docs/STATE.md`'s
  preamble became the pointer to it. The four ledger preambles took 10.0.0 immutability with its
  `Corrected by`/`Superseded by` pointers and the 10.3.0 `[cited]` carve-out, plus 14.0.0 **Paths in
  rows**; `AGENTS.md` gained the ledger exception to its ground-truth rule, the 14.0.0 state
  reading/writing steps and 10.1.0's note that the branch clause, not the push rail, is now the
  enforcement; and Session discipline 5 took the plan-path citation ban.
- 2026-09-02..04 — **The harness train: AMH 9.1.0 → 14.0.0, its prose-debt guard, and the Codex
  rail (DC-029…DC-035).** Shipped scripts and manifest copied with `.gitattributes` against a
  Windows CRLF false failure; `AMH_PROSE_VERSION` plus a `doc-facts.sh` warn/fail tier and 8 fixtures
  now carry the 14.0.0-scripts/9.1.0-prose split in the permanent tier; the Codex hook claim was
  measured on 0.152.1 and 0.153.2 and reworded to declared-but-not-observed across `AGENTS.md` and
  both adapters, with `docs/HARNESS_LOCAL.md` added to `RULE_FILES` (owner); the `:platform` suite
  called unrunnable here went 145/145 on an x86_64 JDK; and the DA-005 review owed on `845bb75`
  returned NOT CLEAN, catching a fail-closed claim that commit had falsified. The seed prose this
  train left owed was landed by DC-036, above.
- 2026-06-23..08-31 — **v1.0.0 → v1.9.2 shipped, then the #126/#127 override-attribution train
  executed and fully read on a device (D-096…DC-028).** `write()` became a transaction reporting what
  Android STORED, used by both detectors, the baseline and the animation band; the commit guard
  gained a ±1 domain deadband and a `MIN_SETTLE_MS` floor; `OverrideDetected` carries its detector
  source; Live Debug gained a **Brightness Writes** card with checks §2 10b–10d; and 1.9.3/vc24
  restored Graph Metrics debug and rolled the ledger to `LEDGER_C.md`. Earlier: DB-073 upgraded AMH
  5.2.0 → 9.1.0 without changing policy, the round script was retired, and all 46 test-only compiler
  warnings cleared with no `src/main` change.
