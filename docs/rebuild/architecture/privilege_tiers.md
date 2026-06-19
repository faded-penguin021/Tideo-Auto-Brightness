# Privilege tiers & grant channels (S12.9c #9)

Source of truth: `platform/.../privilege/PrivilegeManager.kt` (+ `ShizukuGrantGateway`). Mirrors Tasker
`task378 _DetectPrivilege`. **Docs only — S14 acts on any change.**

## Tiers (`enum Tier { NONE, BASIC, ELEVATED }`)

Detection is a first-hit probe (`AndroidPrivilegeManager.detectTier`), highest-first, re-run on
`refresh()` each cycle so a post-start grant is picked up:

| Tier | Preflight | Grants | Capability |
|---|---|---|---|
| **ELEVATED** | `checkSelfPermission(WRITE_SECURE_SETTINGS) == GRANTED` | one-time `pm grant` (ADB / Shizuku / root) | Full core pipeline **+ super dimming** (secure `reduce_bright_colors` via `Settings.Secure`). |
| **BASIC** | `Settings.System.canWrite(context)` | user toggle on the "Modify system settings" screen | Full core pipeline (read sensor → curve → write `Settings.System` brightness + animation). No super dimming. |
| **NONE** | neither | — | Degraded: UI + onboarding only; brightness writes throw `SecurityException` and are swallowed (G1-F1), so the app never crashes unprivileged. |

`Tier` is ordered (`ELEVATED > BASIC > NONE`); gates compare with `>=`. The tier is published as a
`StateFlow<Tier>` so onboarding/Dashboard react live.

## ELEVATED grant channels

`WRITE_SECURE_SETTINGS` is `signature|privileged` and cannot be requested at runtime; it must be
*granted* through one of three channels. **Shizuku is only a grant channel — never a runtime binder
dependency** (CLAUDE.md).

1. **ADB (always offered, the invariant).** `adbGrantInstruction()` returns
   `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`. Requires no companion app and is
   shown regardless of Shizuku state. Tests assert it is always present.
2. **Shizuku** (`ShizukuGrantGateway`). Three-state readiness (`ShizukuAvailability`, S12.9b):
   - `RUNNING` → one-tap "Use Shizuku": bind the user service (with `withTimeoutOrNull(BIND_TIMEOUT_MS)`,
     so no indefinite hang — D-069), request the Shizuku permission, then the bound user service execs
     `pm grant`. No Java reflection (owner caution); AIDL `IShizukuUserService`.
   - `INSTALLED_NOT_RUNNING` → "start Shizuku" prompt (PackageManager finds `moe.shizuku.privileged.api`).
   - `NOT_INSTALLED` → Shizuku path hidden; ADB still offered.
3. **Root** (`tryGrantViaRoot()`). `su -c "pm grant …"`; returns success only if the tier actually rises
   to ELEVATED afterward. Best-effort, swallows failure.

On any successful grant, `refresh()` runs before the result callback so the new tier is visible
immediately.

## Provenance / invariants

- `task378` first-hit order: WRITE_SECURE → WRITE_SETTINGS → NONE (D-016).
- The Tasker `adbwp` pref MUST NOT be read (D-024); BASIC truth = `canWrite`, ELEVATED truth =
  `checkPermission` — never a stored flag.
- "ADB always offered" is a hard invariant (asserted in `PrivilegeManagerTest`).
