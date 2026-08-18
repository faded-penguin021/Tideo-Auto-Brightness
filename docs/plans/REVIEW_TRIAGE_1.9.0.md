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

## Verification limits (state honestly in the commit)

This container holds a **shallow clone: 50 commits, no tags**, and the review's baseline
`d17387e` is not present. The diff range, the 283/13,882/9,265 stat counts, and the
`git log`/`git diff --check` results **could not be replayed**. That is not evidence
against the review — it reviewed a full clone. Every verdict below comes from reading the
current worktree, which is where the claims are anyway.

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

1. **Stay-awake mask** — add `BatteryManager.BATTERY_PLUGGED_DOCK` to `STAY_ON_ANY_CHARGER`
   in `SecureDisplayController.kt`. Test: enabling writes 15, not 7. Do **not** adopt the
   review's canonical-7 test.
2. **Daltonizer representability** — follow the DB-045/DB-042 pattern already in this file:
   nullable/unrepresentable sentinel in `DeviceDisplaySnapshot`, preserve the stored value on
   read-back, skip the write unless the user explicitly picked a mode. Requires **replacing**
   `daltonizer_enabledWithUnrecognizedValue_readsAsOff`, not adding beside it.
3. **Listener leak** — in `LocationReader.activeFix()`, remove updates before the
   `SecurityException` path's `resume(null)`; `invokeOnCancellation` does not fire on a
   normal resume.
4. **Dead branch** — delete the unreachable `if (providers.isEmpty())` in `activeFix()`; the
   preceding `.ifEmpty { listOf(PASSIVE_PROVIDER) }` makes it unreachable.
5. **Test seam** — have `ControlReceiver.onReceive()` call `tryAcquireCommand()` /
   `releaseCommand()` instead of touching `commandInFlight` directly, so the admission test
   exercises production.
6. **Field-aware Apply (optional)** — `applyNow()` writes every field while
   `DisplayTogglesCoordinator.applyLocked()` already diff-writes. Worth closing the
   asymmetry, but it is a hardening measure, not a fix for items 1–2.

## Verification

- `scripts/ladder.sh --guards-only` — docs-only change; must stay green.
- Confirm `docs/plans/` trips nothing: `ledger-prefix.sh` scans only `docs/LEDGER*.md`
  and `comment-budget.sh` reads source, so a new markdown file is out of scope.
- `docs/STATE.md` is at 13 KB of a 14 KB soft cap — **do not** add to it here; that is why
  this plan lands in `docs/plans/`.
- Full `scripts/ladder.sh` is expected to fail in this container on Robolectric artifact
  fetches (network), matching what the review itself reported. That is environmental.

## Scope

Approved action is **only** committing this plan to `docs/plans/` on
`claude/branch-review-verification-ok3lc6`. No production code, ledger, or STATE edits.
