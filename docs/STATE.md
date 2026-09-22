# STATE — project state & session memory

> **Length guard (DA-004).** Thresholds are in `amh.conf`; the compression rules, and the rule for
> what may sit in `Current state` at all, are `docs/RUNBOOK.md` → **Working-memory compression**,
> and they bind whether or not you follow this pointer. Prose-only — no guard judges it.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

Harness AMH 14.1.0 (DC-029), upstream manifest scripts immutable; live ledger `LEDGER_C.md`;
`AMH_PROSE_VERSION` equals `AMH_VERSION` so `doc-facts.sh` is quiet on the pair (DC-031, DC-036),
and 14.1.0's one hand step, the adapter hook shell pin, is applied (DC-051).

**Resuming cold?** Release standing is NOT recorded here — the session banner computes it live via
`scripts/session-facts.sh` (DC-030), settled by hand with
`git ls-remote --tags --refs origin 'refs/tags/v*'`; a later session re-caching a release number is
unguarded.

This branch carries Graph Metrics (DC-001), the `LEDGER_C` rollover, the executed #126/#127
override-attribution work (DC-002…DC-028), the harness units (DC-029…DC-036), the runtime rot
audit (DC-042…DC-046) and the OxygenOS night-light mechanism rows (DC-053, DC-054). Device rounds
on 1.10.0-debug vc24 are **all closed** — the owner ruled no fix on the 0–4095 scale beneath the
app-facing Settings API, freezing the conversion path as built, and a later build owes its own run
(DC-011…DC-013, DC-025…DC-028, DB-083; checks in `DEVICE_TEST_SCRIPT.md` §2). No round script is
alive (RUNBOOK §6, DB-010), the force-stop investigation stays closed (DB-051…DB-060), and
Scorecard.dev is a run-once local input.

## Active work

**Real-device E2E suite** — plan `docs/plans/DEVICE_E2E_PLAN.md` (owner-approved 2026-09-13,
Sol- and Astra-reviewed). No device mutation before S4's recovery contract is Sol-reviewed. S0
(the plan) is done; S1–S9 are open and named in the plan, which S9 deletes.

**Circadian + Night Light fix** — plan `docs/plans/NIGHT_LIGHT_CIRCADIAN_FIX.md` (owner fix spec,
2026-09-21; Astra chose the anchor's home, Sol reviewed both units). The issue is
`faded-penguin021/AdvancedAutoBrightness#15`, not a Tideo issue — a bare `#15` resolves wrongly.

- [x] N1 F1+F2, the reported bug (DC-055, DC-056) · [ ] N2 F4, the hardcoded AOSP Kelvin bounds
  · [ ] N3 F5, daytime activation — BLOCKED on device evidence, ship no guess · [ ] N4 close-out
  (delete the plan). F3 needs no code and is not a segment.

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

1. **Backlog, NOT for this train — extract the 29 hardcoded diagnostic-card labels the widened i18n
   ratchet surfaced (DC-040).** Frozen at 29 by `WRAPPER_CEILING` in `HardcodedStringCheckTest`; the
   ceiling may only fall. Settles it:
   `./gradlew :app:testDebugUnitTest --tests '*HardcodedStringCheck*'` — green means the debt has
   not grown, not that it is gone.

2. **[2026-09-18, retargeted 2026-09-22] Tag v1.11.0 when you are ready — the version is set,
   tagging is yours.** v1.10.1 was never tagged, so it is superseded rather than skipped. Before
   tagging, RUNBOOK §6 wants a green `fdroid-compat.yml` on the release commit (Actions → F-Droid
   compatibility → Run workflow if none has run) and carries a one-shot DA-026 check owed by the
   first release after the AGP 8.13.2 bump. Settles it:
   `git ls-remote --tags --refs origin 'refs/tags/v1.11.0'` — a hit means it is done and this item
   can go.

Open questions:

- **[2026-09-21] Should the reporter of AAB issue 15 get a reply, and may we ask them for two
  outputs?** DB-082's standing rule is that no issue gets a reply unasked, so nothing has been
  posted. A draft reply is in the Night Light plan: it answers their direct question (their reading
  of "Device default" was right for the static path, wrong for the circadian one — that divergence
  was the bug), confirms the two findings they root-caused, corrects their Scale Spread premise,
  and tells them the feature they originally requested already exists (Circadian scaling OFF +
  "Follow circadian scaling" ON). Two things only they can supply: the three
  `cmd overlay lookup config_nightDisplayColorTemperature{Min,Max,Default}` values, which decide
  whether their 686 K floor is a real resource value or a reading of the 0–100 % intensity slider
  and so size F4's blast radius; and `settings get secure night_display_auto_mode`, which says
  whether AOSP's own service is a third party in the F5 activation fight. Options: (a) stay silent
  per DB-082, (b) reply and ask, (c) ask for the two outputs only. Recommendation: (b) — N3 is
  blocked without their evidence, and they filed a correct, well-diagnosed report.

**This train is a minor, `1.11.0`, and vc25 is still its ONE bump (owner, 2026-09-22).** The
override-attribution train shipped as `1.10.0` / vc24 (2026-08-30). This train opened at `1.10.1` /
vc25 as a patch for the backup fix; DC-057 is a new capability, so the owner re-opened it as a
minor, and since `1.10.1` never shipped it became `1.11.0` on the same unshipped vc25 rather than
a second bump. The E2E suite is test tooling and ships nothing, so it moves no version field: land
further user-facing fixes by editing `changelogs/25.txt` (500-character cap), NOT by bumping per
fix and NOT by creating `26.txt`. Re-open only for something genuinely major, and say so.

## Decided non-items

- **No migration resets an already-snowballed `nightLightTemperature` (owner, 2026-09-21;
  DC-055).** A stored Kelvin cannot be told apart from a setpoint the user genuinely chose, so a
  blanket reset to null would discard real choices while missing contaminated profiles that
  currently have circadian off. DC-055 stops the capture; an affected user clears it with the
  screen's existing "device default" button. Do not propose a one-shot reset.
- **Both backup-fix questions are closed (owner, 2026-09-18; DC-052)** — the pre-fix blast radius
  will not be measured, needing `bmgr` work the owner declines, and no regression test guards
  `android:backupAgent`, one having been dropped as YAGNI; propose neither.
- **The `stop()`/`emergencyStop()` join asymmetry stays** (owner, 2026-09-07; DC-047).
- **Issues #123, #126 and #127 get no reply, and no issue gets one unasked** (owner, 2026-08-24,
  re-confirmed 2026-09-07; DB-082) — a standing rule, and nothing was posted.
- **Three device checks will never be executed (owner, 2026-09-07)** — the Android 12/12L Wi-Fi fix
  (DB-074), the unrecognised-colour-mode button (DB-071, DB-078) and Night Light / always-on failing
  safely (DB-041…DB-043) all need hardware the owner lacks.
- **Graph Metrics is owner-tested; its automated wiring tests are declined** (owner, 2026-09-07;
  DC-001).
- **The x86_64-JDK-on-ARM rule is a local host concern** (owner, 2026-09-07; DC-033,
  `.orch/LOCAL_LADDER.md`).
- Still declined: root changelog, speculative dependency bumps, standalone drift audit, Gradle
  dependency verification, wider session-branch CI, the D-162/DA-021 triage sets (DB-038), the
  superseded Privileged Display schedule and a persisted seed without real reports (D-150–152), a
  grayscale quick action, refresh-rate/OEM keys, manual Extra Dim, panic re-firing after teardown,
  §11.39a C1/C2 as wontfix, the destructive `bmgr restore` re-verification (DB-013), and migrating
  the test-only `ContextsContent` wrapper; the rest is in `docs/plans/REVIEW_TRIAGE_1.9.0.md`.
- **Never synthesise unsupported display values on a device** (DB-071) — use a real settings UI;
  DB-077 is exempt, mask 7 having been written by Tideo v1.9.0.
- Rejected by the #126/#127 plan and not to be reintroduced: keying wake behaviour on
  `ACTION_USER_PRESENT`/unlock (owner, 2026-08-30), a larger fixed or blanket settle window, wake
  baseline adoption, a recent-write token set (D-034/D-051(d)), and auto-learning the device
  maximum.

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-09-22 — **Night Light Kelvin reaches the display service where the key is ignored
  (DC-057).** Owner answered the `ColorDisplayManager` question with the refined (c): the secure
  setting stays the write, and a build observed to ignore it also gets the Kelvin through the
  `color_display` binder via Shizuku or root. Shizuku runtime uses are now three. Owner-confirmed
  on the OnePlus 13; the owner ruled it a minor, so the unshipped vc25 became `1.11.0`. DC-058
  fixes the two gaps the PR-time check found: main-thread teardown and the service-off Apply.

- 2026-09-21 — **The circadian night anchor is the device's own Kelvin (DC-056).** A null
  setpoint no longer resolves to the 2850 K constant; the device is read once when the ramp takes
  the temperature key, kept in a `display_prefs` store across process death, and put back when the
  ramp lets go — on a profile swap, on service stop and, by owner decision, on panic. Revises the
  D-154 and D-155 clauses whose premise was that Tideo had no record of the displaced value.

- 2026-09-21 — **The Night-Light snowball is closed (DC-055).** A null `nightLightTemperature`
  ("device default") no longer adopts a device number on read-back, so the D-154 ramp's own last
  sample can no longer be frozen into the profile and ratcheted toward 4082 K on each circadian
  off/on cycle. Reported against v1.10.0 as the bug comment on AAB issue 15; `changelogs/25.txt`
  carries the user-facing line.

- 2026-09-19 — **OxygenOS night-light mechanism pinned, and the shell route found open**
  (DC-053, DC-054). `ColorDisplayService` does not observe `night_display_color_temperature` there,
  so Tideo's writes land in the settings table while the service's state diverges; shell does hold
  `CONTROL_DISPLAY_COLOR_TRANSFORMS`, so a Shizuku-proxied `ColorDisplayManager` call is a live but
  unimplemented route — see the Owner queue.
- 2026-09-18 — **The backup-agent fix, the bump to 1.10.1 / vc25, and two CI corrections.**
  `android:backupAgent` had named a class that does not exist since 1.8.2/vc20, fixed from the
  `rolling-beans` fork (DC-052), opening this train's one bump with `changelogs/25.txt`. The
  Robolectric cache path was also wrong in every workflow — the resolver writes `~/.m2`, not
  `~/.robolectric`, so all three reported a hit while re-downloading ~340 MB, and they still share
  one key — and the two release workflows now drop the obsolete `tools` package as `build.yml`
  does, neither running on a PR.
- 2026-09-13 — **Real-device E2E suite planned (S0)** — see Active work.
- 2026-09-10 — **AMH 14.0.0 → 14.1.0**, whose only hand step the owner applied (DC-050, DC-051).
- 2026-09-07 — **PR #129's hand-checked actions bump; the runtime rot audit, its device
  confirmation and the queue clear-out (DC-042…DC-049).**
- 2026-09-02..06 — **The harness train AMH 9.1.0 → 14.0.0 (DC-029…DC-036), and PR #128's
  fresh-context review with its four fixes (DC-037…DC-041).**
- 2026-06-23..08-31 — **v1.0.0 → v1.9.2 shipped, then the #126/#127 override-attribution train
  executed and read on a device (D-096…DC-028).**
