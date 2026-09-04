# STATE — project state & session memory

> **Length guard (DA-004).** `guard_state_size` and `guard_state_structure` are the authority — read
> them, not a summary. On a warning run ONE deep pass to BOTH floors, folding whole completed stages;
> shaving words cannot meet the sentence floor. Nothing reads WHAT a pass deleted, and the Owner queue
> is protected only by its heading, so compress an item's prose, never drop it.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

Harness AMH 14.0.0 (DC-029), upstream manifest scripts immutable; live ledger `LEDGER_C.md`.

**Resuming cold?** Release standing is NOT recorded here. The session banner computes it live —
this tree's `versionName`/`versionCode`, the newest `v*` tag on origin, and whether this version is
released — via `scripts/session-facts.sh` (DC-030); settle it by hand with
`git ls-remote --tags --refs origin 'refs/tags/v*'`. This file was written to AMH 14.0.0's
tree-relative practice, but that rule is NOT yet legislation here: its binding restatement in
`AGENTS.md`, `docs/RUNBOOK.md` and this file's own length-guard preamble is part of the seed prose
still owed (see Changelog). The split itself no longer depends on this file to stay known:
`AMH_PROSE_VERSION=9.1.0` in `amh.conf` carries it against `AMH_VERSION=14.0.0`, `doc-facts.sh`
warns every run while they differ and fails if `AGENTS.md` drops its disclosure, and
`HARNESS_LOCAL.md` "Upgrading" reads the changelog forward from the prose key so the owed notes
stay reachable (DC-031). Still true and unguarded: nothing stops a later session re-caching a
release number.

This branch carries the Graph Metrics restoration (DC-001), the LEDGER_C rollover, and the executed
#126/#127 override-attribution work (DC-002…DC-009); its plan was deleted at completion by owner
decision, as the lifecycle requires. **Read on a device on 2026-08-31** against 1.10.0-debug vc24,
§2 10b, 10c and 10d all passed and the brightness-maximum question closed with the 12-bit scale
BELOW the app-facing Settings API; the owner ruled no fix, so the conversion path is frozen as
built (DC-011…DC-013, DC-025…DC-028). That is a dated observation, not a standing guarantee — a
later build owes its own run. No round script is alive (RUNBOOK §6, DB-010), the closed force-stop
investigation stays closed (DB-051…DB-060), and Scorecard.dev is a run-once local input, not a
retained score or CI gate.

## Owner queue

> Protected by D-167. Test observable claims before restating them; preserve unresolved items.
>
> **Plain language here — exempt from the tree's terse, ledger-ID-first register (DB-079, owner,
> 2026-08-23)** because a person decides from it: say what to do, on what, and what result means it
> worked, with ledger IDs at the end. Name the command that settles an observable claim, and keep the
> Open questions format — fork, options, recommendation (D-167), dated (DA-006). Credential leaks and
> external-content escalations land here too.

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

**Device rounds (owner, 1.10.0-debug vc24), all now closed** — readings in the rows, checks in
`DEVICE_TEST_SCRIPT.md` §2. 2026-08-31: **10b passed** on the shell ceiling (S = 4095), `raw(d+1)`
quiet with a fresh `DISMISSED_DRIFT (OBSERVER)` against a `raw(d+2)` control that paused, plus that
same quiet injection **pausing on v1.9.2** — as a build with no deadband must, and kept as the
negative control that stops this being a check the phone can never fail (DB-083, DC-027, DC-028).
Also 2026-08-31: **10d read**, the stored value settling at `4095` under Tideo while the card read
five 255s (DC-025). 2026-08-30: the Graph Metrics flash verified and **10c passed** on the card
rather than by inference (DC-011…DC-013).

**Decided (owner).** This train ships as a **minor**, `1.10.0` / vc24, set in `app/build.gradle.kts`
(2026-08-30). On the 10d reading: **no fix — the split scale is the device's reality, so work around
it rather than engineer for it**, which freezes the conversion path as built, declines auto-learning
the device maximum and the `context.resources` move, and leaves DC-003's two trades standing
(DC-026).

4. **Nothing to do — set `JAVA_HOME` to an x86_64 JDK before the ladder on a Windows-on-ARM host
   (2026-09-02, DC-033).** Not a preference: on an aarch64 JVM the `:platform` Robolectric suite
   fails 120 of 145 for a native conscrypt does not publish, and on an x86_64 JVM under Windows's
   emulation the same suite is 145/145. Temurin 21 x64 is also CI's exact vendor, version and
   architecture, so it is the closer environment as well as the working one. Two host settings ride
   with it, both now on: Developer Mode plus `MSYS=winsymlinks:nativestrict`, without which Git
   Bash's `ln -s` silently writes a copy, and a real `python3` ahead of the Microsoft Store alias,
   which otherwise answers `command -v` and exits 0 without running Python.
5. **Nothing to do — the DA-005 review owed on the DC-031 commit is discharged, and it was NOT
   clean (2026-09-04, DC-035).** Run over `4e22273..HEAD` by a fresh-context Codex reviewer, now
   that `codex` is installed here. It confirmed the two things the item asked about — `Upgrading`
   really reads forward from `AMH_PROSE_VERSION`, and `doc-facts.sh` warns at 2 and fails at 1 with
   fixtures pinning both — and found the contradiction nobody had: `HARNESS_LOCAL.md` still said all
   eight repo-local guards fail closed, which `845bb75` had made false. Fixed in the same unit, with
   two lower findings in this session's own work. The PR #128 hold is lifted on this ground alone;
   the branch is otherwise unchanged.

Everything raised through 2026-08-26 is closed in DB-054…DB-085 and DC-001, including the vc22 and
vc23 rounds now folded into `DEVICE_TEST_SCRIPT.md` and the wake false-pause fix that taught DB-083.
The owed fresh-context review of `b462e56..HEAD` is discharged by this train's two adversarial passes
(commit bodies, DC-003/DC-008/DC-009). The two items raised on 2026-09-02 are also closed: the owner
directed the `AGENTS.md` Codex-rail reconciliation, which landed with the two adapter comments it
contradicted (DC-032), and the local-ladder question dissolved rather than being decided — the cause
was the JVM's architecture, not the host, so the WSL2 option the owner had picked is no longer needed
(DC-033, standing instruction above).

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

- 2026-09-04 — **The Codex hook finding re-measured on 0.153.2, before its own unit was committed
  (DC-034).** The version-scoped wording DC-032 had just landed already named a version nobody here
  runs. Measuring again rather than editing the numeral gave the same result — sole `config.toml` at
  `~/.codex/config.toml`, no banner in four `codex exec` runs — and, being taken on Linux where the
  first was Windows, it also retires the Windows-only-quirk hypothesis. Both versions are now named
  in `AGENTS.md`, the two adapter comments and the `HARNESS_LOCAL` table; the earlier ledger rows keep
  0.152.1 alone, since they record what was measured when they were written. `docs/HARNESS_LOCAL.md`
  joined `RULE_FILES` in the same unit (owner, 2026-09-04): it holds the most detailed form of this
  claim and the adapter coverage table, and the tripwire had not covered it.

- 2026-09-03 — **The 14.0.0 version claim got a durable disclosure and a guard (DC-031).** Review of
  `4e22273` found the owed seed prose was recorded only in compressible working memory while the
  claim it qualified sat in `amh.conf`, and that `HARNESS_LOCAL.md` "Upgrading" sent the next
  upgrade session forward from `AMH_VERSION` — past the very notes still owed. Added
  `AMH_PROSE_VERSION`, a `doc-facts.sh` warn/fail tier with 8 fixtures, the `AGENTS.md` disclosure
  paragraph whose literal sentence the guard matches, the widened extension-point list, and
  `gradlew`/`*.py` eol pins. **Landed WITHOUT its DA-005 rule review — see Owner queue 5**; seed
  prose itself is still owed and unchanged as the next unit.

- 2026-09-02 — **The Codex rail wording reconciled and the local ladder recovered (DC-032, DC-033).**
  `AGENTS.md` stopped claiming a Codex pre-shell hook that runs; the rule review found the same claim
  in `.codex/config.toml` and the opposite overclaim in `.claude/settings.json`, and all three now say
  declared-but-not-observed, scoped to the version measured. Separately, the `:platform` suite that
  had been called unrunnable here went 145/145 on an x86_64 JDK: the missing conscrypt native was a
  property of the JVM, not of the laptop.

- 2026-09-02 — **Harness upgraded AMH 9.1.0 → 14.0.0 (DC-029).** Shipped scripts and manifest
  copied, `.gitattributes` installed and the tree renormalised to fix a Windows CRLF false failure
  in `redact.sh --self-test`, version surfaces moved in `amh.conf`/`AGENTS.md`/this file/
  `HARNESS_LOCAL.md`, `ADAPTER_FILES` corrected, and the generated duplicate `ci.yml` deleted in
  favour of `build.yml`. The hand-applied seed prose for 9.2.0 and MAJORs 10.0.0…14.0.0 is
  **still owed** and is the next harness unit — it rewrites `RULE_FILES` prose and lands separately.

- 2026-08-30..31 — **The #126/#127 override-attribution train, executed and now fully read on a
  device (DC-002…DC-028).** `write()` became a transaction reporting what Android STORED, used by
  both detectors, the baseline and the animation band's normalization shift; the commit guard gained
  a ±1 domain deadband and a `MIN_SETTLE_MS` yield floor; a non-MANUAL mode is reclaimed and
  dismissed rather than labelled user input (`parity_gaps.md` dev-01/dev-02);
  `OverrideDetected` carries its detector source; and Live Debug gained a **Brightness Writes** card
  with checks §2 10b–10d. Change 3 did NOT land as written, since restoring a settle fallback to the
  throttle would undo D-062(2)/F71 (DC-005), and two adversarial reviews ran, the second finding a
  band-detector blocker and four siblings (DC-008/DC-009). The device rounds then cost more than the
  code — two checks written against the wrong coordinate system, a sticky pause latch disarming every
  check after a pause, two consequences drawn off the card and withdrawn — until 10d settled it: the
  0–4095 scale sits BELOW the app-facing Settings API, nothing is capped, the owner ruled **no fix**,
  and the rebuilt 10b passed with its v1.9.2 negative control (DC-010…DC-028).
- 2026-08-26 — Bumped to `1.9.3` / vc24 with `fastlane/…/changelogs/24.txt`, restored Graph Metrics
  debug (%AAB_Debug 7) to chart (re)draws deduped by a content signature, deleted the miscategorised
  `PipelineCycleRunner` cycle-time emit, and rolled the ledger to `LEDGER_C.md` in the SAME commit
  (DC-001).
- 2026-06-23..08-25 — v1.0.0 → v1.9.2 shipped, DB-073 upgraded AMH 5.2.0 → 9.1.0 without changing
  policy, the round script was retired, all 46 test-only compiler warnings cleared with no
  `src/main` change, and DB-074…DB-085 fixed; durable detail is D-096…DB-085.
