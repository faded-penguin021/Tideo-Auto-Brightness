# Decided non-items

Binding declines, moved verbatim from `docs/STATE.md` → **Decided non-items** at the owner's grant
of 2026-10-06 (DD-037), which keeps the header and a pointer here. They are legislation, not
working memory: change them only under the rule-review protocol (`docs/PROTOCOLS.md`), and do not
propose an item listed here unless the owner reopens it.

- **No migration resets an already-snowballed `nightLightTemperature` (owner, 2026-09-21;
  DC-055).** A stored Kelvin cannot be told apart from a setpoint the user genuinely chose, so a
  blanket reset to null would discard real choices while missing contaminated profiles that
  currently have circadian off. DC-055 stops the capture; an affected user clears it with the
  screen's existing "device default" button. Do not propose a one-shot reset.
- **Both backup-fix questions are closed (owner, 2026-09-18; DC-052)** — the pre-fix blast radius
  will not be measured, needing `bmgr` work the owner declines, and no regression test guards
  `android:backupAgent`, one having been dropped as YAGNI; propose neither. The owner later ran
  one package-scoped restore check (2026-09-27): it found and fixed the missing
  `fullBackupOnly` and verified the restore path end to end (DD-008).
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
  §11.39a C1/C2 as wontfix, a scripted `bmgr restore` step (DB-013; the owner ran it once by
  hand, DD-008), and migrating
  the test-only `ContextsContent` wrapper; the rest is in `docs/plans/REVIEW_TRIAGE_1.9.0.md`.
- **Deliberate differences retained by the light-refactor decision (owner, 2026-10-21; DE-049).**
  Do not propose unifying these contracts unless the owner reopens them:
  - Alpha remains the transcribed Tasker preview; the brightness chart retains its base curve,
    labelled as the base curve, and its final-output marker (D4; the label has not landed,
    DE-049).
  - The ordinary-mode dimming chart remains the transcribed Tasker graph, without the circadian
    multiplier (DE-046).
  - The wizard evaluator retains its distinct Tasker bounds and power handling, and its candidate
    generation its `form3a` floor (DE-049).
  - The brightness setting, display calibration and window brightness retain their own API
    contracts (DE-036). This does not bar a change to any one of them, DE-019's included.
  - Override tolerances 2 and 1 answer different questions (D-054, DC-005).
  - `evaluate`/`calculatedBrightness` (DE-049) and `round3`/`bigScale3` (D-031) retain their
    rounding contracts.
  - Per-screen lux precision (DE-049) and domain/app animation defaults (DD-030) retain their
    distinct uses.
  - Night Light retains separate service-on/off state machines (DD-059, DD-092); this does not bar
    the planned fixes or a shared write-order helper.
- **Never synthesise unsupported display values on a device** (DB-071) — use a real settings UI;
  DB-077 is exempt, mask 7 having been written by Tideo v1.9.0.
- Rejected by the #126/#127 plan and not to be reintroduced: keying wake behaviour on
  `ACTION_USER_PRESENT`/unlock (owner, 2026-08-30), a larger fixed or blanket settle window, wake
  baseline adoption, a recent-write token set (D-034/D-051(d)), and auto-learning the device
  maximum.
