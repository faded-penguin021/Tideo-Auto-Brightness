# Plan — Tideo real-device E2E suite (hybrid ADB + UI automation)

> Owner-approved execution plan (playbook 5, multi-session). Its segment checklist is mirrored in
> `docs/STATE.md` → `## Active work`. It is provisional: delete it at the final segment, once its
> durable content lives in STATE Changelog lines and ledger rows. Ledger rows and code never cite
> this path.
>
> Reviews folded in:
> - gpt-5.6-sol on the first draft;
> - gpt-6-astra second pass, 2026-09-13. Its twelve findings are applied in place, with the one
>   factual correction noted in §2 "Brightness scale S".

## Context

`docs/rebuild/DEVICE_TEST_SCRIPT.md` (§0–§15) is today a manual owner pass. The goal is an
executable, individually addressable E2E suite against the owner's real phone:
- deterministic wherever Android exposes state;
- UI automation only where it adds value;
- safe for the owner's data.

A previous agent wiped app data, so safety is designed in first. **No device mutation is authorised
under this plan until segment S4's recovery contract is implemented, interruption-tested and
Sol-reviewed.**

**Environment (measured 2026-09-13)**
- Claude runs in the arm64 dev container: Python 3.12, no pip, uv or adb.
- Builds run on the amd64 builder container over SSH (`assembleDebug` has taken ~39 min).
- The host is Windows 11 ARM64 with Docker Desktop. Its compose files are outside the repo.
- The phone is attached to Windows. `host.docker.internal` resolves; port 5037 was refused when
  probed.
- Packages: debug `com.tideo.autobrightness.debug`, release `com.tideo.autobrightness`. They install
  side by side.
- Debug builds are signed with each machine's own keystore.

**Signals verified in code**
- Logcat carries no pipeline state.
- The ongoing notification and the override alert both use ID 1001
  (`AmbientMonitoringService.kt:487,625`) and are told apart by channel (`ambient_monitoring` /
  `manual_override`).
- Compose tags exist (`service_switch`, `override_card`, `resume_button`, `automation_toggle`,
  `value_<tag>`), but `testTagsAsResourceId` is not enabled.

**Indirect writes Tideo makes, which any scenario can trigger** (the reason for §3's effect
inventory):
- **PANIC**
  - persists `serviceEnabled=false` (`AmbientMonitoringService.kt:442`);
  - writes every display toggle to its default (`DisplayTogglesCoordinator.kt:106-117`);
  - zeroes Extra Dim (`SuperDimmingCoordinator.kt:157`).
- **Service start/stop**
  - start stores and stop removes the private `saved_brightness_mode`
    (`ScreenBrightnessController.kt:147-167`);
  - display teardown reapplies the baseline (`DisplayTogglesCoordinator.kt:87`);
  - ordinary teardown can leave one late pipeline write by accepted design (STATE decided
    non-item, DC-047 — not to be "fixed").
- **Screen on** clears `contextOverride` and re-evaluates contexts
  (`AmbientMonitoringService.kt:107`).
- **Context evaluation** writes whole profiles and saves or clears baseline snapshots
  (`ContextEngine.kt:463`).
- **`LOAD_PROFILE`** clears the baseline, persists the user-profile name and replaces the settings
  (`ProfileApplier.kt:20-37`). `CONTEXTS_RESUME` does not restore the prior configuration.
- **Automation enabled** emits global `STATE_CHANGED` broadcasts that the owner's Tasker/MacroDroid
  may act on (`AmbientMonitoringService.kt:370`, `docs/AUTOMATION.md`).
- **Service start** can set `debug.hwui.force_dark` through Shizuku/root when that opt-in is on
  (`ForceDarkController.kt:24`).

**Owner decisions (2026-09-13)**
- mobile-use is an optional extra, never the oracle.
- The container reaches the host adb server over TCP.
- The harness may force-stop the release app if its service is running.
- Allowed: WRITE_SECURE_SETTINGS grant/revoke on the debug package; PANIC and force-stop of the
  debug package; Tideo prefs through the UI with restore.
- No reboot.
- No Windows username, email, device serial or other personal identifier in any file under `e2e/`.

## 1. Decision: mobile-use over Artemis (a ledger row in segment S1)

Both are Apache-2.0, need Python ≥3.12, and are LLM agents on LangGraph + adbutils + uiautomator2.
Evidence comes from shallow clones: mobile-use `12a1dbd`, artemis `371aa6d`.

**Artemis is rejected.** It auto-installs an Accessibility Helper APK and writes
`secure enabled_accessibility_services` (`artemis/runtime/helper_manager.py:601-660`). That is the
key holding Tideo's own `AabToastAccessibilityService`, which §12 depends on. It is also far heavier:
opencv, scipy, matplotlib, jupyter-client, ffmpeg, an admin console.

**mobile-use is chosen, but only as an offline triage tool.** Its live device-control hazards:
- `pm uninstall --user 0 dev.mobile.maestro` (`clients/ui_automator_client.py:143-178`);
- `send_text` → `set_fastinput_ime`, which installs or uninstalls an IME APK (uiautomator2
  `_input.py:73-76`);
- uncontrolled taps.

So it is used in the smaller mode: an LLM triage of **already-captured, sanitised evidence**, with
**no device connection at all**. It never decides a result.

**The core suite uses uiautomator2 + adbutils directly.** Sol recommended cutting the agent layer;
the owner kept it, so it is the last segment.

## 2. Architecture

A new top-level `e2e/`. It stays outside `RULE_FILES` and the citation-scan paths. The only
production change is one testability hook.

```
e2e/
  pyproject.toml, uv.lock   # uv project; core: adbutils, uiautomator2, pytest (exact pins)
                            # optional extra "triage": mobile-use (pinned SHA), no device access
  README.md                 # setup, run one/all, safety + recovery model, manual-only list
  host/windows-adb.md, host/start-adb-server.ps1   # host glue ONLY; placeholders, never literal paths
  tideo_e2e/
    device.py    # the ONLY adb gateway: exact allowlisted command templates, deny-by-default
    ui.py        # uiautomator2 wrapper with a per-scenario UI allowlist (see §3.2)
    effects.py   # effect inventory: per scenario, what it may write, directly or through Tideo
    journal.py   # identity-bound, locked, conflict-aware recovery journal (§3.4)
    recovery.py  # the single serialized recovery procedure (§3.5)
    state.py     # readers: settings, dumpsys (services, notification-by-channel, package), prefs digest
    tideo.py     # Tideo verbs: service on/off, resume, Live Debug rows, relaunch after process death
    evidence.py  # sanitised evidence under e2e/evidence; raw captures to the private store (§3.7)
  tests/         # test_s00_smoke, test_s02_override, test_s05_panic, test_s06_elevated,
                 # test_s11_privileged, test_s13_automation, test_manifest; unit/ (device-free)
  scenarios.toml # one row per script step: id, status auto|partial|manual, effects, test nodeid, reason
  run.sh         # --preflight (strictly read-only), --recover (explicit), pytest passthrough (-k/-m)
```

**Running scenarios**
- Scenario IDs match the script (`s02_10b`), and pytest supplies PASS/FAIL/SKIP.
- `test_manifest.py` fails if a script step is missing from `scenarios.toml`, if an `auto` row points
  at nothing, or if a test's runtime effects are not declared in its row.
- The Markdown stays as the human spec. Each section gains a one-line pointer to its e2e IDs
  (additive, never renumbered).

**Deterministic oracles only.** Each needs a verified distinct starting condition and a positive,
bounded, attributable effect, so an already-true state can never pass it:
- **Paused:** a notification on channel `manual_override` **and** `override_card` in the hierarchy.
  Before the injection, verify neither is present.
- **Disposition:** the "Override seen" timestamp must be newer than the injection, and
  observed/settled/expected must match the injected domain values. Only then read the disposition
  text (DC-015).
- **Quiet/control pairs** (10a/10b/10c/42/32a): two asserts in one test, so a silent build FAILs
  (DB-083).
- **§13 verbs, one oracle each:**
  - `SERVICE_ON`: verified OFF before, FGS present after.
  - `SERVICE_OFF` / `SERVICE_TOGGLE`: the verified transition in both directions.
  - `PAUSE`: verified unpaused before, paused state after.
  - `RESUME`: paused before, unpaused after; also ignored with the master switch off, where the
    service must stay absent.
  - `REAPPLY`: **partial.** An attributable fresh write cannot be forced without controlling lux.
  - `PANIC`: a distinct non-S brightness before the command and an observed transition to S after.
    It runs in the late PANIC stage (§3.3).

**Brightness scale S**
- S comes from the owner's hand measurement in a device profile (`e2e/devices/<model>.toml`,
  `stored_max`). On the owner's phone it is 4095, per DEVICE_TEST_SCRIPT §2 10b.
- Preflight only sanity-checks that the current `screen_brightness` ≤ S.
- **Do not calibrate S through PANIC:** it would calibrate the endpoint PANIC is later judged by.
- *Correction to Astra finding 9:* `config_screenBrightnessSettingMaximum` is the app-facing scale M
  (255), not the stored scale S (DC-025), so it cannot stand in for S.

**Testability hook**
- Add `Modifier.semantics { testTagsAsResourceId = true }` on the root `Surface` in
  `AutoBrightnessApp.kt:27`.
- Add it to the root of any `Dialog` an automated scenario touches.
- Pin it with a Robolectric semantics test beside `SemanticsAudit`.

## 3. Device safety (built, interruption-tested and Sol-reviewed before any device contact)

1. **Command boundary.**
   - `device.py` accepts only **exact allowlisted command templates** with typed, validated
     parameters: package names from a fixed set, settings keys from the effect inventory, integer
     ranges, no free paths.
   - Everything else raises before sending. That includes uiautomator2/adbutils internals, which
     are audited at the adb service-open level (`shell:`, `exec:`, `sync:`, install/uninstall).
   - uiautomator2 APIs that mutate packages or IMEs are patched to raise: `app_clear`,
     `app_uninstall*`, `app_install`, `set_fastinput_ime`, `send_keys`, `set_input_ime`,
     `show_touches`/`pointer_location`.
   - uiautomator2's own disclosed side effect is pushing `u2.jar` to `/data/local/tmp`.
   - A unit test greps the pinned dependency sources for install/uninstall/clear/rm/settings. It is a
     **drift alarm, not proof** of confinement.
   - **Hard denylist** (unit-tested with evasion variants):
     - `pm clear`, any uninstall, install outside the guard;
     - `rm`, `settings delete|reset`;
     - `wipe`, `recovery`, `bootloader`, `fastboot`, `reboot`;
     - `setprop`, `cmd overlay`, `appops reset`, `bmgr`, `content delete|update|insert`, mutating
       `run-as`;
     - force-stop of any other package.
2. **UI boundary** (independent of the command boundary). An allowed tap can still trigger
   destructive app behaviour, so `ui.py` only acts through each scenario's declared allowlist
   entries: (package or window, selector, action).
   - The foreground package/window is verified before every action.
   - Coordinates are never used.
   - Anything outside the allowlist raises. Examples: profile delete/overwrite/restore-factory,
     import, context-rule editors, Calibrate.
   - The notification shade is allowed only for the named Resume/Reset actions on Tideo's own
     notification.
3. **Effect inventory and ordering.**
   - Every scenario declares its effects: direct writes, and indirect Tideo writes from the list in
     Context.
   - Preflight, the journal and execution order all derive from that one inventory.
   - Order is by declared effects, not by section. Read-only and settings-only scenarios run first.
     Anything with PANIC (§5 14a, §11 39, **§13 43's PANIC**), revoke or force-stop runs last.
   - **Profile-changing scenarios are manual:** §13 44, §8, and anything issuing `LOAD_PROFILE` or
     `CONTEXTS_RESUME`. Restoring the full (settings, baseline, profile-name) tuple is not
     supported.
4. **Recovery journal** (`journal.py`).
   - **Binding.** Each entry is bound to device identity (a salted hash of the serial, never the
     serial itself), Android user and package identity (`firstInstallTime`, versionCode, cert
     digest).
   - **Single-run lock.**
   - **Write-ahead.** Each entry records the original value and the last value the harness expects,
     fsync'd **before** the mutation.
   - **Conflict-aware.** Recovery restores a key only if its current value equals the expected
     value, i.e. the change is attributable to the run. A mismatch (the owner changed it meanwhile,
     or Tideo did) is a **conflict**: never overwritten, kept in the journal, reported for owner
     resolution.
   - **Explicit recovery.** Recovery is never automatic and never part of `--preflight`, which is
     strictly read-only. A pending journal makes preflight and every run refuse to start until
     `run.sh --recover` has completed or the owner resolves it. `--recover` verifies identity before
     its first mutation.
5. **One serialized, idempotent recovery procedure** (`recovery.py`), used by test teardown, signal
   handlers and `--recover` alike:
   1. **Quiesce writers:** service off through `service_switch` or `SERVICE_OFF`; wait for FGS
      absence. The accepted DC-047 late write is absorbed by step 4, not prevented.
   2. **Restore grants and Tideo prefs** through the UI, with the service still disabled.
   3. **Restore device settings,** conflict-aware as in §3.4.
   4. **Verify stability:** re-read all journaled keys after a settle window. Values must be
      unchanged across two reads N seconds apart.
   5. **Restore the original runtime state deliberately:** service on if it was on, and the original
      paused state where it is reconstructable. Otherwise report it. Then re-verify the keys the
      service start may touch.
   - If the device disconnects, the remaining journal entries stay pending and nothing is dropped.
   - **Unit tests inject an interruption at every stage** and prove that a rerun of `--recover`
     converges.
6. **What is snapshotted** (read-only, at preflight and per scenario per its effects):
   - every settings key in the inventory:
     - system `screen_brightness`, `screen_brightness_mode`;
     - secure `night_display_activated/_color_temperature/_auto_mode`,
       `accessibility_display_daltonizer(_enabled)`, `accessibility_display_inversion_enabled`,
       `doze_always_on`, `reduce_bright_colors_level/_activated`;
     - global `stay_on_while_plugged_in`, `user_disabled_hdr_formats`,
       `are_user_disabled_hdr_formats_allowed`;
     - the prop `debug.hwui.force_dark`;
   - WRITE_SECURE_SETTINGS grant state;
   - runtime state: FGS running, paused;
   - Tideo's private logical state, read through read-only `run-as … cat`:
     - the settings DataStore (`serviceEnabled`, `contextOverride`, …);
     - the context baseline store and user-profile name;
     - `saved_brightness_mode`;
     - the context-rule set (enabled count).

   These are stored as digests plus the fields needed for invariants.

   **Preflight SKIP rules:**
   - Any scenario whose effects could create an absent settings row (PANIC, Privileged Apply) is
     skipped: restoring it would need `settings delete`.
   - If enabled context rules exist, or `contextOverride`/baseline is set, every scenario whose
     effects include service start, screen wake or context re-evaluation is skipped, unless the
     owner explicitly confirms for that run.
   - If automation is, or would be, enabled, scenarios are skipped unless the owner confirms that no
     Tasker/MacroDroid profile receives `STATE_CHANGED`. This covers §13, and any service test
     while automation is already on.
   - If the force-dark opt-in is on, service-start scenarios are skipped.
   - Post-run invariants: every private logical digest either matches or differs only in declared
     churn (WorkManager, `service_health`). Anything else FAILs the run and is reported.
7. **Private data and evidence** (identifier rule).
   - **Raw captures never go under `e2e/`.** These are app-data archives, unredacted
     dumpsys/logcat, screenshots and hierarchies.
   - They go to a private store outside the repo, `${XDG_STATE_HOME:-$HOME/.local/state}/tideo-e2e/`
     (mode 0700). That is the persistent home volume the owner authorised for this purpose. Recovery
     archives (read-only `run-as … tar c` of `files shared_prefs no_backup databases`) live only
     there, for **owner-driven** recovery; the harness never writes app data back.
   - `e2e/evidence/` (gitignored) receives only **sanitised, allowlisted** text: assertion output,
     selected settings values, the redacted command log, and hierarchy nodes filtered to the Tideo
     package with text redaction outside known labels.
   - A scan runs **before** anything is persisted there, covering serial-looking tokens, emails,
     account names, host user paths and SSIDs.
   - The mobile-use triage reads **only** the sanitised evidence, never the private store.
   - A device-free unit test scans all tracked `e2e/` files for identifier shapes.
8. **Install guard**
   - Artifact identity: `aapt dump badging` shows `com.tideo.autobrightness.debug`; record sha256 and
     commit.
   - Its cert must equal the installed package's cert (apksigner on the pulled installed base.apk).
   - versionCode ≥ installed.
   - The owner approves after seeing installed vs new (versionName, versionCode, commit).
   - `install -r` only; never `-d`/`-g`/uninstall.
   - On a cert mismatch: STOP.
   - `lastUpdateTime`/`versionCode` are modelled as expected changes.
9. **Other registered mutations:**
   - `pm grant/revoke` WRITE_SECURE_SETTINGS on the debug package. Revoke kills the process; the
     scenario expects the death and relaunches.
   - `am broadcast` to the debug ControlReceiver, full component name, at least 1.5 s apart.
   - `input keyevent` SLEEP/WAKEUP, with the screen awake at the end.
   - Force-stop of the debug package, followed by the §3.5 restore.
   - Force-stop of the release package, only if its FGS is running (owner-approved). At the end,
     `am start` its launcher activity to lift the stopped state, and report whether its service came
     back.

## 4. Scenario conversion (`scenarios.toml`; initial targets)

**Auto**
- §0 tier badge readout.
- §1 4.
- §2 8/9, 10a, 10b, 10c, 10e, each with the DC-012 unpaused gate before every half.
- §6 16.
- §6 19b: never loosened. If it fails, classify it from evidence; Sol noted the Dashboard never calls
  `refreshTier()`.
- §6 19a.
- §11 32a, 32c, 34, 36 (revoke → process death handled), 39b.
- §13 42, 43 (per-verb oracles above; `REAPPLY` partial; `PANIC` late), 45.
- Late PANIC stage: §5 14a (broadcast + notification Reset, distinct pre-value, transition to S),
  §11 39.

All of the above are subject to the §3.6 preflight SKIP rules.

**Partial**
- §1 5–7, §2 10 (light).
- §3 11 (off/on readouts automated).
- §5 14/15/15a/15b (gesture, vibration).
- §6 17–19 (dark room).
- §9 29, once a concrete field and bound are pinned from code.
- §11 32 (visible agreement).
- §12 40/41 (hierarchy label audit only; uiautomator bounds ≠ Compose touch bounds).
- §13 43 `REAPPLY`, 44a, 46.
- §14 47–50 (IME/bar overlap geometry only).

**Manual** (reason recorded)
- §0 1–3.
- §3 12, §15 54 (no reboot).
- §4.
- §7.
- §8, **§13 44** (profile load: an unrestorable tuple).
- §9 26–28.
- §10.
- §11 32b (`settings delete`), 32d, 33, 35, 37, 38, 39a, 39c, 39d.
- §15 51–53, 55.

## 5. Environment

- **Portable.** `e2e/bootstrap.sh` installs a pinned `uv` into `~/.local/bin` (the persistent home
  volume) and runs `uv sync --frozen`.
  - No apt packages and no Linux adb. adbutils talks to the host server at
    `host.docker.internal:5037`, configured in `run.sh`.
  - Versions come from `uv.lock`.
  - An optional Dockerfile snippet for baking `uv` into the image is documented only. The compose
    files are outside the repo.
- **Host glue** (`e2e/host/`, owner-run): Google platform-tools adb on Windows ARM64 (x64 under
  Prism).
  - First try the default server through Docker Desktop's loopback proxy.
  - Otherwise `adb -a nodaemon server start`, plus a firewall rule allowing TCP 5037 only from the
    Docker/WSL vEthernet subnet.
- **Triage extra:**
  - `MOBILE_USE_TELEMETRY_ENABLED=false`.
  - No `ADB_HOST` and no device.
  - Input is sanitised evidence only.
  - The API key is read by the tool itself and never printed.
- **`.gitignore`:** `e2e/.state/`, `e2e/evidence/`, `e2e/.venv/`.

## 6. Segments (sequential; each ends shippable)

Each segment ends with: acceptance green → STATE Changelog line → commit → push, on the assigned
`claude/…` session branch. A follow-up session bases on the unmerged predecessor. Checklist mirrored
in STATE `## Active work`.

- **S0 — plan**
  - This file + the STATE checklist, pushed. No code.
- **S1 — decision + scaffolding**
  - Ledger row: framework decision + safety/recovery model, recording delivered content without
    this path.
  - `e2e/` project, pinned deps, identifier scan, manifest test skeleton, `scenarios.toml` with every
    step classified.
  - **Acceptance:** device-free unit tests + `scripts/ladder.sh --guards-only`.
- **S2 — command and UI boundary**
  - `device.py`, `ui.py`, denylist/allowlist with evasion tests, dependency drift alarm.
  - Sol review beside the writes.
  - **Acceptance:** unit tests.
- **S3 — effects, journal, recovery**
  - `effects.py`, `journal.py`, `recovery.py`, with interruption injected at every stage,
    identity-mismatch refusal, conflict preservation.
  - **Acceptance:** unit tests.
- **S4 — testability hook + scenarios**
  - Root `testTagsAsResourceId` + Robolectric test; scenario tests.
  - **Acceptance:** full `scripts/ladder.sh` green, including `:app:assembleDebug`.
  - **Blocking Sol review of S2–S4** before any device contact. Sol gets the full boundary,
    journal and recovery code, the effect inventory, the install guard and every mutating test.
    Safety findings get fixed first.
- **S5 — device, read-only**
  - The owner starts the host adb server.
  - `run.sh --preflight`, strictly read-only: connectivity; identity; package, variant and cert;
    release FGS; settings, runtime and private-state snapshot; SKIP-rule evaluation; private-store
    archive.
  - The owner answers the confirmations (contexts, automation receivers).
- **S6 — device, install + smoke**
  - Owner-approved guarded install.
  - `-m smoke`: service on/off, tags visible as resource-ids, one journaled settings round trip,
    `--recover` on an empty journal.
- **S7 — device, suites by effect order**
  - Settings-only → service/wake → grant → revoke/force-stop → PANIC.
  - After each group: journal empty, invariants clean.
  - Failures are classified as Tideo defect / harness / framework / timing / OEM / invalid
    assumption. Fix in scope and rerun the node, then the group.
- **S8 — triage extra**
  - Offline mobile-use triage on one sanitised failure bundle.
- **S9 — close-out**
  - Ladder green; full auto suite; restoration and invariant report; `git diff` identifier review.
  - Final Sol review on safety and on whether each test can fail on a broken build.
  - DEVICE_TEST_SCRIPT pointers, RUNBOOK device-suite playbook, final ledger/STATE.
  - **Delete this plan file.**

## Verification

- **Device-free:** `e2e/run.sh pytest tests/unit` covers boundary evasions, the UI allowlist,
  journal identity/conflict/lock, interruption at every recovery stage, conversion maths, manifest
  completeness and the identifier scan. Also run `scripts/ladder.sh`.
- **Device:** `run.sh --preflight` → `-m smoke` → effect-ordered groups → `run.sh --recover` on an
  empty journal as the final no-op check. Record JUnit XML + sanitised evidence.
- **Report:**
  - the decision;
  - the architecture;
  - the auto/partial/manual table;
  - results;
  - defects and fixes;
  - dependencies (from `uv.lock`);
  - limitations;
  - restoration and invariant confirmation;
  - the redacted audit log of what touched the phone.
