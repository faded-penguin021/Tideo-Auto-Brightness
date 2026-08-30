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
six segments landed and the durable content is in the ledger. The 2026-08-30 card/slider
disagreement over the brightness maximum is **resolved — both numbers are right** (DC-017); what is
still open is only whether the 12-bit scale sits below the app-facing Settings API (DC-021, the
leading account), which §2 10d reads, and §2 10b stays re-opened. No round script is alive (RUNBOOK §6, DB-010). Do **not** re-open the closed
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
3. **Two device readings are left (1.10.0-debug vc24), and §2 10d has one trap.** **TIDEO must be
   what drives the brightness to the top** — bright room or raised Min/Max Brightness, service
   running and unpaused, let it settle, touch nothing. A 2026-08-30 attempt read `4095` after the
   SYSTEM SLIDER was dragged to maximum, which measures the slider and settles nothing. Then
   `adb shell settings get system screen_brightness` (`screen_brightness_float` is `null` here,
   DC-024) and fill in the table in the step: only the settled stored value moves, ≈`4095` meaning
   the 12-bit scale sits below the app API and nothing is capped, ≈`255` meaning the top 94% really
   is unreachable. Do **not** pair the card's "Current brightness" with the adb value at one instant
   (DC-018). **§2 10b must still be re-run** recording "Last override" after each half, even though
   DC-021 now explains its quiet half — a model that predicts a silence is not a reading of it
   (DC-022). **Change no conversion behaviour until 10d is read** — the deferred attribution trades
   (DC-003) and the rejection of auto-learning both still stand.

4. **Backlog, owner-approved 2026-08-30 but NOT for this train — give the Graph Metrics wiring real
   tests.** Nothing today covers `ChartCanvas` calling the sink, the sink being null below level 7, or
   the signature dedupe suppressing a repeat draw; the one test that looks like it does passes
   unchanged on `b462e56`, which is why the owner's device observation below is still the feature's
   only evidence. Contained Compose work, to be picked up as its own unit (DC-001).

Open questions:

- **[2026-08-30, ANSWERED — no action] Card `Device max: 255` vs slider `4095` — which is wrong?**
  Neither; see the changelog and DC-017/DC-018. Nothing to do beyond reading §2 10d.

- **[2026-08-30] Two cleanups, both waiting on 10d — take them together or not at all?** (i) Move
  `deviceMax` from `Resources.getSystem()`, documented as ignoring runtime resource overlays, to
  `context.resources`, which honours them: a no-op here (both give 255) and latent hygiene for a
  phone that ships such an overlay (DC-019). (ii) Rename `deviceMax`/`requestedRaw`/
  `acknowledgedRaw` to `settingsApiMax`/`requestedSettingValue`/`readBackSettingValue` with matching
  Live Debug labels — all three name hardware and all three are app-facing Settings API values, and
  that misreading is what produced DC-014 (DC-023). **Recommendation: both, in one unit, straight
  after 10d is read** — (i) is conversion-path and frozen until then, and (ii) renames two card
  labels that §2 10b/10d cite verbatim, so landing it mid-round hands the owner a script that no
  longer matches the APK.

**Device round 2026-08-30 (owner, 1.10.0-debug vc24).** **Graph Metrics flash: verified** — the
owner reports the level-7 checks all fine, still the feature's only evidence until item 4 lands.
**§2 10c: passed**, and confirmed on the card rather than inferred — `Last override: DISMISSED_MODE
(OBSERVER)`, `Mode at commit: Not manual`, `Manual override: No`, so the pipeline was not paused and
the run was not vacuous (DC-011…DC-013). **§2 10b: NOT passed — re-opened.** Its quiet half was
explained here as quantisation on a 12-bit scale; the card then read `Device max: 255`, so the app
converts on the identity branch and both `+20` injections were 20 domain apart. Far outside the
deadband, so the quiet one was called **unexplained** (DC-014). **DC-021 supersedes that framing:**
if the 12-bit scale sits below the app API, the shell's 181 and 201 reach the app as 11 and 13, so
the injections were 1 and 3 domain apart and DC-010's arithmetic was right with the conversion in
the wrong place. Same round, same direction: its starting raw `161` = round(10 × 4095/255) exactly,
which the identity branch cannot have written (DC-020). 10b is still re-run rather than passed —
"Last override" after each half, a fresh `DISMISSED_DRIFT` being the pass and a stale timestamp
meaning the monitor dropped the event upstream (DC-015, DC-022).

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

- 2026-08-30 — **The card/slider disagreement is answered, and the leading explanation is that the
  12-bit scale sits BELOW the app-facing Settings API.** `cmd overlay lookup` returns 255 from
  android's default configuration with no overlay, so `config_screenBrightnessSettingMaximum` really
  is 255 there, `deviceMax` reads it correctly, and the discovery block is byte-identical to
  `v1.9.2`, so no regression is possible (DC-017). On that reading the shell's 181/201 reach the app
  as 11/13, DC-010's arithmetic was right with the conversion in the wrong place, and DC-014's "20
  domain apart" is void — but it stays a hypothesis, and 10b stays re-run rather than passed, since
  a model that predicts a silence is not a reading of it (DC-021, DC-022). §2 10d is rebuilt around
  a SETTLED stored value, the card-versus-adb pair being void — a ratio near 16 fits both
  hypotheses (DC-018) — and an attempt read `4095` off the SYSTEM SLIDER, which measures the slider
  (DC-020, DC-024). Queued behind 10d: `context.resources`, and renaming `deviceMax`/`requestedRaw`/
  `acknowledgedRaw`, which name hardware and are all Settings API values (DC-019, DC-023).
- 2026-08-30 — **How that question arose.** The vc24 round verified the Graph Metrics flash and
  passed §2 10c (mode 1 + raw 4000, quiet — attributable because the distance is far outside the
  deadband, so an OEM-cleared mode would have paused instead, DC-013). It also broke two checks:
  10b injected RAW offsets to test a DOMAIN rule (DC-010) and 10c read a bare mode `0` an OEM can
  produce by itself (DC-011); and it exposed the sticky pause latch by which 10b's control disarms
  every check after it (DC-012). Reading `Device max: 255` off the card then drew two consequences,
  both withdrawn on the owner's report of normal behaviour (DC-014…DC-016).
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
