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

Harness AMH 9.1.0 (DB-073), upstream manifest scripts immutable; live ledger `LEDGER_C.md`.

**Resuming cold?** **v1.9.2/vc23 is the newest release** — tag `v1.9.2` → `c7c96dc` on `main`
(`git ls-remote --tags origin` settles it). This branch is at **`1.10.0` / vc24** with
`fastlane/…/changelogs/24.txt`, carrying the Graph Metrics restoration (DC-001), the LEDGER_C
rollover and the executed #126/#127 override-attribution work (DC-002…DC-009), whose plan
`docs/plans/OVERRIDE_ATTRIBUTION_1.9.3.md` is **retained by owner instruction** rather than deleted
at its final segment as playbook 5 would have it. **The train is complete and nothing is owed on a
device:** §2 10b, 10c and 10d have all been read on 1.10.0-debug
vc24, the brightness-maximum question is closed with the 12-bit scale BELOW the app-facing Settings
API, and the owner ruled no fix, so the conversion path is frozen as built (DC-011…DC-013,
DC-025…DC-028). No round script is alive (RUNBOOK §6, DB-010), the closed force-stop investigation
stays closed (DB-051…DB-060), and Scorecard.dev is a run-once local input, not a retained score or
CI gate.

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
