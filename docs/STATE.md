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

4. **Decide whether `AGENTS.md` still claims a Codex rail that does not run (2026-09-02, DC-030).**
   The Conventions section says "Codex's pre-shell hook runs the shipped command guard" and refers to
   "its shell hook". On codex CLI 0.152.1 neither declared hook was observed to fire: run
   `codex doctor` in this repo and read the Configuration block — it names `~/.codex/config.toml` as
   the only config source and lists no project layer — then run
   `codex exec -s read-only -c windows.sandbox="unelevated" "print hello"` and grep the output for
   the `AMH session start` banner, which `scripts/session-start.sh` prints unconditionally. If the
   banner is absent, those two sentences overstate the rail. Changing them is a constitution edit, so
   it is yours, not a session's. `docs/HARNESS_LOCAL.md` already records the observation and its
   limits. Note the honest ceiling: no repository check can prove a hook fired, so the fix is wording
   scoped to what was measured, not a new claim in the other direction.
5. **The local ladder cannot go green on this Windows ARM64 laptop, and that is the host, not the
   tree (2026-09-02).** `:platform`'s Robolectric tests fail 120/145 with
   `UnsatisfiedLinkError: no conscrypt_openjdk_jni-windows-aarch_64` — conscrypt publishes no native
   for Windows on ARM. `:domain` (the golden vectors), compilation, lint and every guard pass; only
   the Android adapter tests are unrunnable. Settle it with
   `./gradlew :platform:testDebugUnitTest --offline` and read the first `<failure>` in
   `platform/build/test-results/testDebugUnitTest/*.xml`. Options: (a) run the ladder under WSL2,
   where conscrypt does ship `linux-aarch_64`; (b) run it in a `linux/amd64` container to match CI
   exactly, at a large speed cost under emulation; (c) accept CI as the authority for this rung and
   rely on `build.yml`. Recommendation: (a) for day-to-day, because it also removes the CRLF class
   of problems, with CI remaining the gate that decides.
6. **BLOCKING — the DC-031 rule changes are committed UNREVIEWED; DA-005 is owed (2026-09-03).**
   Two fresh-context attempts failed in the session environment, not in the tree: one died in a
   container restart, one deadlocked 40+ minutes taking no further tool rounds; `codex` is not
   installed, so the `.codex` reviewer was unavailable too. DA-005 has no self-review fallback and
   the session authored the diff, so it is parked rather than self-certified. Run the DA-005
   checklist from a session that can hold a fresh context, against `git diff 4e22273..HEAD`; test
   above all that `HARNESS_LOCAL.md` "Upgrading" really reads forward from `AMH_PROSE_VERSION`, that
   `doc-facts.sh` warns at 2 and fails at 1 on the right branches, and that no prose claims
   enforcement the tree lacks. **Do not merge PR #128 first.** Ladder green and 8 new fixtures pass,
   but green is not the review (DC-031).

Everything raised through 2026-08-26 is closed in DB-054…DB-085 and DC-001, including the vc22 and
vc23 rounds now folded into `DEVICE_TEST_SCRIPT.md` and the wake false-pause fix that taught DB-083.
The owed fresh-context review of `b462e56..HEAD` is discharged by this train's two adversarial passes
(commit bodies, DC-003/DC-008/DC-009).

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

- 2026-09-03 — **The 14.0.0 version claim got a durable disclosure and a guard (DC-031).** Review of
  `4e22273` found the owed seed prose was recorded only in compressible working memory while the
  claim it qualified sat in `amh.conf`, and that `HARNESS_LOCAL.md` "Upgrading" sent the next
  upgrade session forward from `AMH_VERSION` — past the very notes still owed. Added
  `AMH_PROSE_VERSION`, a `doc-facts.sh` warn/fail tier with 8 fixtures, the `AGENTS.md` disclosure
  paragraph whose literal sentence the guard matches, the widened extension-point list, and
  `gradlew`/`*.py` eol pins. **Landed WITHOUT its DA-005 rule review — see Owner queue 6**; seed
  prose itself is still owed and unchanged as the next unit.

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
