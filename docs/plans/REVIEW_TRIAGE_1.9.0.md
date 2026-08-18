# Triage of the Codex full-branch review (v1.9.0)

## Context

Codex delivered a full-branch review of the unreleased v1.9.0 train and recommended
**"Do not release v1.9.0"** on two Major findings. `docs/STATE.md` currently records the
opposite posture — *"Nothing now blocks it"* — reached after owner device verification
(DB-060 fixed, DB-061 shown pre-existing in v1.8.2).

This plan verifies each claim against the code as **data, not truth**, and records what
should actually be done. The outcome: **every mechanism the review describes is real, but
neither Major is release-blocking**, one Major is *under*-diagnosed in a way that matters
more than the review's own framing, and its prescribed fix would codify a worse bug.

The single approved action is committing this plan to `docs/plans/`.

## Baseline: replayed and confirmed

Initially unverifiable — the container held a shallow 50-commit clone with no tags, and the
review's `d17387e` baseline was absent. After `git fetch --unshallow` the range replays and
**the review's process claims check out exactly**:

- `d17387e` **is** the `v1.8.2` tag.
- `git diff --shortstat d17387e 618972c` = **283 files, 13,882 insertions, 9,265 deletions** —
  precisely the counts reported.

So the review measured what it said it measured, against the right baseline. The severity
disagreements below are disagreements about *judgement*, not about its arithmetic.

## Verdicts

| ID | Mechanism | Severity verdict | Action |
|---|---|---|---|
| `V190-MAJOR-001` daltonizer→OFF | **Confirmed** | **Overstated** — no AOSP trigger exists | Minor, v1.9.1 |
| `V190-MAJOR-002` stay-awake mask | **Confirmed** | **Under-diagnosed**; prescription is wrong | v1.9.1, but *not* as prescribed |
| `WAIT-MINOR-001` field-aware writes | **Confirmed** | Asymmetry, not absence | Defer |
| `WAIT-MINOR-002` listener leak | **Confirmed** | Real, rare, cheap | v1.9.1 |
| `WAIT-MINOR-003` `ContextsContent` | **Confirmed test-only** | **YAGNI — decline** | Decided non-items |
| `WAIT-NIT-001` dead branch | **Confirmed** | Trivial | v1.9.1 |
| `WAIT-NIT-002` test seam | **Confirmed** | Trivial | v1.9.1 |

**Release recommendation: reject the hold.** Nothing found blocks the v1.9.0 tag.

## MAJOR-001 — real defect, unevidenced trigger

Mechanism confirmed exactly as described: `readDaltonizer()` maps enabled-but-unrecognized
to `OFF`; `DeviceDisplaySnapshot.daltonizer` is non-nullable while its siblings are
nullable sentinels; `applyNow()` calls `setDaltonizer` unconditionally.

It genuinely violates the repo's own cited law (DB-045: *"A Boolean editor must not
normalize a state it cannot represent"*), and the review is right that HDR already got
this treatment while daltonizer did not.

**Two things the review missed, both of which cut against "release-blocking":**

1. **There is no AOSP path that produces an unknown value.** AOSP's constant set is
   `{-1, 0, 11, 12, 13}` — verified against `AccessibilityManager` — which is *exactly*
   `DaltonizerMode`'s set. Triggering this requires an OEM writing a non-AOSP value to the
   AOSP key, which the review asserts as a scenario but never evidences. Contrast MAJOR-002,
   where a mainstream trigger provably exists.
2. **The behavior is locked by a deliberate, named test** —
   `daltonizer_enabledWithUnrecognizedValue_readsAsOff` in `SecureDisplayControllerTest.kt`,
   plus an explaining code comment. This is a considered decision, not an oversight. Fixing
   it means *changing an asserted contract*, which the review presents as simply adding code.

Damage is also recoverable (re-enable in Settings) and gated behind ELEVATED + service-off
+ Apply. **Verdict: Minor. The user's read is correct.**

## MAJOR-002 — the review found the right field for the wrong reason

The described harm (a partial mask *broadened* to all chargers) is real but rare. The
**common** case runs the other way and the review missed it entirely:

- AOSP's `StayAwakePreferenceController` writes
  `BATTERY_PLUGGED_AC|USB|WIRELESS|DOCK` = **15** (verified against AOSP source).
- Tideo's `STAY_ON_ANY_CHARGER` is `AC|USB|WIRELESS` = **7** — it predates
  `BATTERY_PLUGGED_DOCK` (API 31).
- So on **every** device where the user enabled Developer Options → "Stay awake",
  the mask is 15, Tideo reads `15 != 0` → `true`, and any Apply writes 7 —
  **silently dropping dock stay-awake.**
- `minSdk = 31` and `BATTERY_PLUGGED_DOCK` is API 31, so there is **no compatibility
  reason** for the omission. The fix is a one-line constant.

**The review's prescribed regression test would codify this bug.** It asks that "the full
AC/USB/wireless mask maps to canonical enabled" — declaring 7 canonical and making AOSP's
actual 15 *custom/unrepresentable*. Under the review's own fix spec the switch would then be
replaced by a preservation notice on stock Android 12+ devices, and Tideo could never turn
stay-awake off. **That is precisely the DB-049 regression this repo already suffered with
HDR** ("An absent row is a defined default, not a custom preference to preserve").

**Field-aware writes do not fix this either.** When the user *intentionally* enables
stay-awake, Tideo still writes 7 and still drops the dock bit. The mask constant is the bug.

**Owner decision: defer to v1.9.1.** v1.9.0 ships with the dock-drop.

## WAIT-MINOR-003 — decline (YAGNI)

`ContextsContent` is confirmed test-only: 13 call sites, all under `app/src/test`, none in
live navigation. The observation is accurate; the **prescription is not worth it**.
Migrating 13 sites to `ProfilesContextsScreen` or a test-only host buys zero user-visible
benefit, and `ScreensInfoA11yTest` currently gets real accessibility coverage through it
that a migration risks quietly losing. The shared `ContextRulesSection` — the part that is
actually live — is unaffected either way. **Recommend recording under
"Privileged Display declines"/`Decided non-items` rather than doing the work.**

## v1.9.1 work items

1. **Stay-awake mask** — **DONE (DB-065, 2026-08-18).** `BatteryManager.BATTERY_PLUGGED_DOCK`
   added to `STAY_ON_ANY_CHARGER` in `SecureDisplayController.kt`; the round-trip test now
   asserts 15, plus a test that an existing 15 survives a re-enable and one that any non-zero
   partial mask still reads as enabled. Both new assertions mutation-proved red against the
   old constant. The review's canonical-7 test was not adopted.
2. **Daltonizer representability** — **DONE (DB-066, 2026-08-18).** `readDaltonizer()` returns
   null for enabled-but-unrecognized; `DeviceDisplaySnapshot.daltonizer` is nullable and
   read-back preserves the stored mode; the direct Apply skips the write while the device is
   unrepresentable unless the draft differs from the committed profile (the user's own pick), so
   the picker still works. The screen shows a preservation notice under the chips.
   `daltonizer_enabledWithUnrecognizedValue_readsAsOff` was **replaced**, not supplemented.
3. **Listener leak** — **DONE (DB-067, 2026-08-18).** `activeFix()` removes updates on the
   `SecurityException` path before `resume(null)`. Not JVM-testable: Robolectric's shadow never
   throws from `requestLocationUpdates` (probed) and there is no mocking library to fake one.
4. **Dead branch** — **DONE (2026-08-18).** The unreachable `if (providers.isEmpty())` is gone.
5. **Test seam** — **DONE (2026-08-18).** `onReceive()` now calls `tryAcquireCommand()` /
   `releaseCommand()`, so `ControlReceiverAdmissionTest` exercises the production pair.
6. **Field-aware Apply (optional)** — **DONE (DB-068, 2026-08-18).** `applyNow()` diffs against a
   device read taken under its own lock; capability-null fields are skipped, and below ELEVATED
   (no snapshot) every write is still attempted so the tier failure reaches `writeFailed`.

## Verification

- `scripts/ladder.sh --guards-only` — docs-only change; must stay green.
- Confirm `docs/plans/` trips nothing: `ledger-prefix.sh` scans only `docs/LEDGER*.md`
  and `comment-budget.sh` reads source, so a new markdown file is out of scope.
- `docs/STATE.md` is at 13 KB of a 14 KB soft cap — **do not** add to it here; that is why
  this plan lands in `docs/plans/`.
- Full `scripts/ladder.sh` was expected to fail in this container on Robolectric artifact fetches
  (network), matching what the review itself reported. **No longer true (2026-08-18):** it runs
  green here, so a red rung is a finding, not the environment. The two standing WARNs are the
  absent local `main` ref, which leaves the poison-token and author-identity rungs checking nothing.

## Scope

As written, the approved action was **only** committing this plan to `docs/plans/` on
`claude/branch-review-verification-ok3lc6` — no production code, ledger, or STATE edits.

**Superseded 2026-08-18:** the owner then asked for the work items to be executed. Items 1–6 were
implemented on `claude/first-work-item-plan-lqklk4` (DB-065…DB-069), one commit each, and carry
their own DONE markers above. This section is kept for the record of what the plan itself was
approved for; the DONE markers are the current state.

### Why the Dependabot action bumps are not adopted here

`dependabot/github_actions/github-actions-8189b25dd7` (7 updates) was checked and **deliberately
left on its own branch**. The supply-chain posture it exists to protect is intact:

- All 12 actions remain **40-hex SHA pins** — zero reversions to tag refs, so Scorecard's
  pinned-dependencies outcome holds.
- All 6 changed SHAs **verify against their claimed upstream tags** (`git ls-remote`).
- All 5 bumped majors declare `runs.using: node24`, satisfying build.yml's Node 24 policy.

It is not adopted into this branch because this is a docs-only triage: merging CI supply-chain
bumps here would put two unrelated review surfaces in one PR, and `dependabot.yml` deliberately
groups these into a single PR "because reviewing them together is how the owner actually reads
them". The bumps also need the workflows to actually *run* against them — particularly the
F-Droid reproducibility job, which round-trips artifacts through
`upload-artifact`/`download-artifact`, both bumped a major. That validation only happens on the
Dependabot PR.

**Two defects to fix on that PR before merging it** — both are DB-038's own predicted decay
("a pin is a snapshot; without a refresh path it decays into a claim nobody rechecks"):

1. `clean-dist.yml` — the SHA moved to v7.0.1's `3d3c42e5…` but the marker still reads
   `# v5.1.0`. Dependabot's marker rewrite fails when trailing prose follows the version, which
   this line has. The same SHA is labelled `v7.0.1` in four other files, and v5.1.0's real SHA
   is `fbc6f399…` (the *old* pin), so the marker is provably the stale half.
2. `build.yml` — the Node 24 policy block still names the **old** majors (`checkout@v5`,
   `cache@v5`, `upload-artifact@v6`, `download-artifact@v7`, `github-script@v8`), every one of
   which this PR bumps. That prose is load-bearing ("Do NOT downgrade any of these") and is now
   factually wrong. Dependabot never edits prose.

Worth noting: **no guard enforces marker↔SHA agreement.** Nothing in `scripts/guards/` or
`scripts/ladder.sh` checks it, so `dependabot.yml`'s claim that the pin and its version marker
"stay in sync" is empirically false for any line carrying a trailing comment. A small guard
comparing each `uses:` SHA against its `# vX.Y.Z` marker would have caught defect 1 mechanically.
