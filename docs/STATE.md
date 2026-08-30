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
rollover, a test-only warning cleanup, and the executed #126/#127 override-attribution work
(DC-002…DC-009), whose plan `docs/plans/OVERRIDE_ATTRIBUTION_1.9.3.md` is **retained by owner
instruction (2026-08-30)** rather than deleted at its final segment as playbook 5 would have it. All
six segments landed and the durable content is in the ledger, but the 2026-08-30 device round left
the Live Debug card and the device disagreeing about the brightness maximum — an open question
below, and the reason §2 10b is re-opened. No round script is alive (RUNBOOK §6, DB-010). Do **not** re-open the closed
force-stop investigation (DB-051…DB-060), and treat Scorecard.dev as a run-once local input rather
than a retained score or CI gate.

## Owner queue

> Protected by D-167. Test observable claims before restating them; preserve unresolved items.
>
> **Plain language here — exempt from the tree's terse, ledger-ID-first register (DB-079, owner,
> 2026-08-23)** because a person decides from it: say what to do, on what, and what result means it
> worked, with ledger IDs at the end. Name the command that settles an observable claim, and keep the
> Open questions format — fork, options, recommendation (D-167), dated (DA-006). Credential leaks and
> external-content escalations land here too.

1. **Nothing to do — three checks are blocked on hardware.** The Android 12/12L Wi-Fi fix needs a
   phone that old and the owner has none (DB-074, §8 24); the unrecognised-colour-mode button only
   appears if a phone reports a mode Android does not know, and none do, so never force one by
   writing a fake value (§11 32c, DB-071, DB-078); and Night Light / always-on display failing safely
   needs a Samsung, every phone to hand reporting them available (DB-041…DB-043).
2. **Nothing to do — issues #123, #126 and #127 get no reply.** Owner's decision (2026-08-24 for
   #123, carried forward by the plan); nothing was posted, and do not comment without the owner
   saying so first (DB-082).
3. **Two device readings are left (1.10.0-debug vc24), and both feed the open question below.**
   **§2 10d** is largely answered already by the card the owner read after 10c — `Requested →
   acknowledged: 10 → 10`, `ACKNOWLEDGED`, `Device max: 255` against a system-slider maximum of
   `4095` — so what remains is the same card at the TOP of the curve (bright room, or raise Min/Max
   Brightness), to see whether the top goes flat as DC-014 predicts. **§2 10b must be re-run** per
   the re-opening below, recording "Last override" after each half rather than only whether it
   paused. **Change no conversion behaviour until the owner answers the open question** — the
   deferred attribution trades (DC-003) and the rejection of auto-learning both still stand.

4. **Backlog, owner-approved 2026-08-30 but NOT for this train — give the Graph Metrics wiring real
   tests.** Nothing today covers `ChartCanvas` calling the sink, the sink being null below level 7, or
   the signature dedupe suppressing a repeat draw; the one test that looks like it does passes
   unchanged on `b462e56`, which is why the owner's device observation below is still the feature's
   only evidence. Contained Compose work, to be picked up as its own unit (DC-001).

Open questions:

- **[2026-08-30] The Live Debug card and the app's actual behaviour disagree about the device
  maximum — which one is wrong?** The 1.10.0-debug card reads `Device max: 255` and `Raw requested:
  10` for domain 10, while the owner's system slider at maximum reads `4095` from `settings get
  system screen_brightness` and reports that brightness and override detection both work normally,
  as they always have. **Nothing is known to be broken for the user** — two consequences claimed
  here earlier were withdrawn (DC-016) — but the numbers cannot both be right, and §2 10d is nothing
  but a reading of that card. **First settle which it is, before anyone proposes a fix:** with the
  service running, read the card's "Current brightness" and `settings get system screen_brightness`
  at the same moment. Equal means the app really writes raw-identity; a ratio near 16 means only the
  display is wrong. If it is the app, note that 1.9.2 behaves correctly, so this train would be the
  regression and the answer is to find it, **not** to auto-learn the maximum, which stays a Decided
  non-item (DC-014, DC-016).

**Device round 2026-08-30 (owner, 1.10.0-debug vc24).** **Graph Metrics flash: verified** — the
owner reports the level-7 checks all fine, still the feature's only evidence until item 4 lands.
**§2 10c: passed**, and confirmed on the card rather than inferred — `Last override: DISMISSED_MODE
(OBSERVER)`, `Mode at commit: Not manual`, `Manual override: No`, so the pipeline was not paused and
the run was not vacuous (DC-011…DC-013). **§2 10b: NOT passed — re-opened.** Its quiet half was
explained here as quantisation on a 12-bit scale; the card then read `Device max: 255`, so the app
converts on the identity branch and both `+20` injections were 20 domain apart. Far outside the
deadband, so the quiet one is **unexplained, not correct** (DC-014). Re-run it reading "Last
override" after each half: a fresh `DISMISSED_DRIFT` is the pass, a stale timestamp means the
monitor dropped the event upstream (DC-015).

**Decided 2026-08-30 (owner).** This train ships as a **minor**, `1.10.0` / vc24, now set in
`app/build.gradle.kts`; `changelogs/24.txt` was already correct since the code did not move. The two
attribution trades are **deferred until the owner has tested the debug APK**, and are deliberately
left as built until then: (a) a foreign write landing between our `putInt` and its read-back is
adopted as ours, so that one override is missed until the next cycle re-points the marker — not fully
fixable, since a clamping device deviates by hundreds of raw units and no tolerance admits the clamp
while excluding a slider move (DC-003); and (b) on a clamping device `runCycle` compares the target
against a normalized `read()`, so every cycle re-sweeps without changing the screen — pre-existing, no
wrong pause (the deadband holds), but permanent on such hardware and a battery cost. Device check 10d
is what settles both. Do not "fix" either speculatively; the plan rejected auto-learning the device
maximum for the same reason.

Everything raised through 2026-08-26 is closed, its detail in the named rows — the LEDGER_C
rollover, DB-081, DB-085, the vc23 device rounds now in `DEVICE_TEST_SCRIPT.md` §7 21a and §11 39d
(DB-084, DB-077, DB-078), the wake false-pause fix with its DB-083 lesson that a device check must
be able to fail on a well-behaved phone, and every earlier round through vc22 (DB-054…DB-072). The
owed fresh-context review of `b462e56..HEAD` is discharged by this train's two adversarial passes
(commit bodies, DC-003/DC-008/DC-009).

## Decided non-items

- Repo/process declines remain: root changelog, speculative dependency bumps, standalone drift
  audit, Gradle dependency verification, wider session-branch CI, the D-162/DA-021 triage sets (SHA
  pinning left this list when Dependabot supplied a refresh path, DB-038).
- Still declined: the superseded Privileged Display schedule and a persisted seed without real
  reports (D-150–152), a grayscale quick action, refresh-rate/OEM keys, manual Extra Dim, panic
  re-firing after teardown, §11.39a C1/C2 as wontfix, and repeating the destructive `bmgr restore`
  verification (DB-013). The test-only `ContextsContent` wrapper stays test-only — migrating its 13
  sites buys nothing and risks its accessibility coverage; that and the rest of the triage are in
  `docs/plans/REVIEW_TRIAGE_1.9.0.md` (`WAIT-MINOR-003`).
- **Never synthesise unsupported display values on a device** (DB-071); use a real settings UI.
  DB-077 is exempt because mask 7 was written by Tideo v1.9.0 and §11 32a is device-verified.
- Rejected by the #126/#127 plan, not to be reintroduced: keying wake behaviour on
  `ACTION_USER_PRESENT`/unlock (owner, 2026-08-30), a larger fixed or blanket settle window, wake
  baseline adoption, a recent-write token set (D-034/D-051(d)), auto-learning the device maximum.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-08-30 — **The Live Debug card reads `Device max: 255` where the system slider reports 4095,
  and that discrepancy is now the open question** — not a diagnosed defect. Two consequences claimed
  from it were withdrawn on the owner's report that brightness and override detection both work
  normally: the detection one was a reasoning error, since the pause test compares against Tideo's
  own last write rather than a second user value (DC-016). **What does survive corrects the entry
  below:** 10b's quiet half had been explained as quantisation on a 12-bit app scale, and the card
  says the app is not converting on that scale, so the explanation is void either way and 10b is
  re-opened as unexplained. 10c stands, confirmed on the card (DC-014, DC-015).
- 2026-08-30 — **§2 10c passed too** (mode 1 + raw 4000, quiet). Recorded why that is attributable
  without the debug card — far outside the deadband, an OEM-cleared mode would have paused instead
  (DC-013) — and fixed the ordering trap it exposed: the pause latch is sticky and clears only on an
  explicit Resume, so 10b's control disarms 10c and every later injected check unless the tester
  re-checks "Manual override" first (DC-012). Only 10d is left.
- 2026-08-30 — **Device round on 1.10.0-debug vc24 (owner):** Graph Metrics flash verified and §2 10b
  passed; fixed the two checks the round broke. 10b injected RAW offsets to test a DOMAIN rule, so
  its control quantised back inside the deadband at 28 start values on a 12-bit panel and would have
  failed a correct build — it now converts to the domain value it wants (DC-010). 10c read the
  brightness mode back as `0` as proof Tideo reclaimed it, which an OEM build can produce by itself,
  so it now requires the `DISMISSED_MODE` disposition (DC-011). 10d is still unrun (Owner queue).
- 2026-08-30 — Owner decided this train is a **minor**: `versionName` 1.9.3 → **1.10.0**, vc24
  unchanged, so `changelogs/24.txt` still applies. The two attribution trades and the Graph Metrics
  test gap are deferred by the owner pending a debug-APK round (Owner queue).
- 2026-08-30 — **Executed the #126/#127 override-attribution plan** across six segments
  (DC-002…DC-009). `write()` is a transaction reporting what Android STORED; both detectors and the
  baseline use that acknowledgement, the animation band shifting by the provider's normalization
  offset so a clamping OR lagging device is explained; the commit guard gained a ±1 domain deadband
  and a `MIN_SETTLE_MS` yield floor; a non-MANUAL mode is reclaimed and dismissed, not labelled user
  input (Tasker deviation, `parity_gaps.md` dev-01/dev-02); `OverrideDetected` carries
  its detector source; Live Debug gained a **Brightness Writes** card; device checks are §2 10b–10d.
  Change 3 was NOT implemented as written — it asked to restore a settle fallback to the throttle,
  which D-062(2)/F71 had removed deliberately and a test pins, so only the yield floor landed
  (DC-005). Two adversarial reviews ran, the second finding a band-detector blocker and four sibling
  defects (DC-008/DC-009).
- 2026-08-26 — Bumped to `1.9.3` / vc24 with `fastlane/…/changelogs/24.txt`, restored Graph Metrics
  debug (%AAB_Debug 7) to chart (re)draws deduped by a content signature, deleted the miscategorised
  `PipelineCycleRunner` cycle-time emit, and rolled the ledger to `LEDGER_C.md` in the SAME commit
  (DC-001).
- 2026-08-23..25 — Retired the round script once 1.9.2 shipped, cleared all 46 test-only compiler
  warnings with no `src/main` change, and fixed DB-074…DB-085 (action pins, live date with fixed
  location, wake-settle handling, review findings).
- 2026-06-23..08-22 — v1.0.0 → v1.9.1 shipped and DB-073 upgraded AMH 5.2.0 → 9.1.0 without changing
  policy; durable detail is D-096…DB-073.
