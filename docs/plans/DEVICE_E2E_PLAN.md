# Plan — Tideo real-device E2E suite (hybrid ADB + UI automation)

## Context

`docs/rebuild/DEVICE_TEST_SCRIPT.md` (§0–§15, ~70 steps) is today a manual owner pass. The goal is
an executable, individually addressable E2E suite against the owner's real phone:
- deterministic wherever Android exposes state;
- UI automation only where it adds value;
- safe for the owner's data.

A previous agent wiped app data, so safety is designed in first.

**Environment (measured this session)**
- This container is `claude-dev`: arm64 Ubuntu 24.04, Python 3.12.3, no pip, uv or adb.
- Builds run on `android-builder` (amd64 under QEMU, reached over SSH; `assembleDebug` has taken
  ~39 min).
- The host is Windows 11 ARM64 with Docker Desktop. Its compose files live outside the repo.
- The phone is attached to Windows. `host.docker.internal` resolves, but port 5037 is refused today.
- Packages: debug `com.tideo.autobrightness.debug`, release `com.tideo.autobrightness`. They install
  side by side.
- Debug builds are signed with each machine's own keystore.

**Signals that are verified in code**
- Logcat carries no pipeline state.
- The ongoing notification and the override alert both use **ID 1001**
  (`AmbientMonitoringService.kt:487,625`). They are told apart by **channel** (`ambient_monitoring`
  vs `manual_override`). ID 1002 is only ever cancelled.
- Compose test tags: `service_switch`, `override_card`, `resume_button`, `automation_toggle`,
  `value_<tag>` rows (`AabCard.kt:129`).
- `testTagsAsResourceId` is not enabled.
- **PANIC persists `serviceEnabled=false`** (`AmbientMonitoringService.kt:442`). It also writes
  every display toggle to its default unconditionally (`DisplayTogglesCoordinator.kt:106-117`) and
  zeroes the Extra Dim rows (`SuperDimmingCoordinator.kt:157`).
- Service start can set `debug.hwui.force_dark` through Shizuku/root when that opt-in is on
  (`ForceDarkController.kt:24`).

**Owner decisions (2026-09-13)**
- mobile-use is an **optional extra**, never the oracle.
- The container reaches the **host adb server** over TCP.
- The harness may **force-stop the release app** if its service is running.
- Allowed operations:
  - WRITE_SECURE_SETTINGS grant/revoke on the debug package;
  - PANIC and force-stop of the debug package;
  - Tideo prefs changed through the UI, then restored.
- **No reboot.**
- **Nothing under `e2e/` may contain a Windows username, email, device serial or any other personal
  identifier.**
- On acceptance, **commit this plan to the repo** (`docs/plans/DEVICE_E2E_PLAN.md`) and nothing
  else. That is the first action.

## 1. Decision: mobile-use over Artemis (recorded as a `docs/LEDGER_C.md` row)

Both frameworks are Apache-2.0, need Python ≥3.12, and are LLM agents on LangGraph + adbutils +
uiautomator2. Evidence comes from shallow clones: mobile-use `12a1dbd`, artemis `371aa6d`.

**Artemis is rejected.**
- It auto-installs an Accessibility Helper APK and writes `secure enabled_accessibility_services`
  (`artemis/runtime/helper_manager.py:601-660`). That is the key holding Tideo's own
  `AabToastAccessibilityService`, and §12 depends on it.
- It is also far heavier: opencv, scipy, matplotlib, jupyter-client, ffmpeg, an admin console.

**mobile-use is chosen, as an optional layer only.**
- It installs no helper APK.
- It supports `ADB_HOST`/`ADB_PORT` (`config.py:35`).
- It has an SDK with structured output, traces, and a telemetry switch.

Hazards to fence off:
- `pm uninstall --user 0 dev.mobile.maestro` (`clients/ui_automator_client.py:143-178`).
- `send_text` → `set_fastinput_ime`, which installs or uninstalls an IME APK (uiautomator2
  `_input.py:73-76`).
- It needs an LLM key.

**Core suite = uiautomator2 + adbutils directly.** Sol recommended cutting the agent layer
(YAGNI). The owner chose to keep it, so it is built **last**, after the deterministic suite is
green. It is never imported by core tests.

## 2. Architecture

A new top-level `e2e/`. It stays outside `RULE_FILES` and the citation-scan paths, and the only
production change is one testability hook.

```
e2e/
  pyproject.toml, uv.lock   # uv project; core: adbutils, uiautomator2, pytest (exact pins)
                            # optional extra "agent": mobile-use (pinned git SHA) — phase last
  README.md                 # setup, run one/all, safety model, manual-only list
  host/windows-adb.md, host/start-adb-server.ps1   # host glue ONLY; uses $env:USERPROFILE, never a literal path
  tideo_e2e/
    device.py   # the ONLY adb gateway; capability-typed mutations, deny-by-default
    safety.py   # classifier + transport-level audit hook + identifier redaction
    txn.py      # SettingsTxn / GrantTxn / TideoPrefTxn with fsync'd journal
    state.py    # settings, dumpsys (services, notification by channel, package), app-data manifest
    ui.py       # uiautomator2 wrapper: resource-id (test tag) → text → desc; never coordinates
    tideo.py    # Tideo verbs: service on/off, resume, read Live Debug rows, relaunch after process death
    evidence.py # on failure: screenshot, hierarchy XML, logcat -d slice, dumpsys, command log (redacted)
  tests/
    conftest.py, test_s00_smoke.py, test_s02_override.py, test_s05_panic.py, test_s06_elevated.py,
    test_s11_privileged.py, test_s13_automation.py, test_manifest.py
    unit/       # device-free: classifier, journal replay, restore-on-exception, conversion, identifier scan
  scenarios.toml  # one row per script step: id, status auto|partial|manual, test nodeid, reason
  run.sh          # wrapper: --preflight, --restore-only, --verify-restored, pytest passthrough (-k/-m)
```

**Running scenarios.** IDs match the script (`s02_10b`, `s11_32a`). pytest gives
PASS/FAIL/SKIP/xfail, so there is no custom runner. `test_manifest.py` fails if any script step is
missing from `scenarios.toml` or an `auto` row points at nothing. The Markdown stays as the human
spec, and each section gains a one-line pointer to its e2e IDs (additive, never renumbered).

**Deterministic oracles only**
- Paused: a posted notification on channel `manual_override` **and** `override_card` in the
  hierarchy.
- Service running: an FGS entry in `dumpsys activity services`.
- Settings: `settings get`.
- Disposition: a **fresh** Live Debug diagnostic. The "Override seen" timestamp must be newer
  than the injection, observed/settled/expected must match the injected domain values, and only then
  is the disposition text read (DC-015).
- Quiet/control pairs (10a/10b/10c/42/32a): two asserts in one test, so a silent build FAILs
  (DB-083).

**Brightness scale S**
- Measured, never assumed.
- If PANIC is permitted by preflight (see §3), the measurement uses the PANIC path:
  `emergencyStop()` writes domain 255 through Tideo's own path, so the settled `screen_brightness`
  equals S.
- Otherwise it falls back to `e2e/devices/<model>.toml` (`stored_max`), and the conversion tests are
  marked `partial` with the reason.

**Testability hook**
- Add `Modifier.semantics { testTagsAsResourceId = true }` on the root `Surface` in
  `AutoBrightnessApp.kt:27`.
- Add it to the root of any `Dialog` that an automated scenario touches. No blanket edits.
- Pin it with a Robolectric semantics test beside `SemanticsAudit`.

## 3. Device safety (built and Sol-reviewed before any device contact)

1. **One gateway, audited at the transport.**
   - `device.py` exposes typed methods only.
   - `safety.py` hooks adbutils at its lowest service-open level (the call that sends `shell:`,
     `exec:`, `sync:`, `install`/`uninstall` service strings). It classifies **argv tokens**, not
     raw strings, so whitespace/path/quoting variants cannot slip through.
   - Unclassified mutations raise before sending.
   - uiautomator2 APIs that mutate packages or IMEs are patched to raise: `app_clear`,
     `app_uninstall*`, `app_install`, `set_fastinput_ime`, `send_keys`, `set_input_ime`,
     `show_touches/pointer_location` setters.
   - Dependencies are pinned exactly, and their call graph for install/uninstall/clear/rm/settings is
     re-audited at the pinned versions (unit test greps the installed sources).
   - uiautomator2's own side effect is limited to pushing `u2.jar` to `/data/local/tmp` and starting
     it with `app_process`. It is disclosed, not app data.
2. **Hard denylist** (unit-tested with evasion variants):
   - `pm clear`, `pm uninstall`/`cmd package uninstall`/adb uninstall, `install` outside the guard;
   - `rm`, `settings delete`, `settings reset`;
   - `wipe`, `recovery`, `bootloader`, `fastboot`, `reboot`;
   - `setprop`, `cmd overlay`, `appops reset`, `bmgr`, `content delete|update|insert`, any `run-as`
     other than read-only `ls|stat|cat|tar c`;
   - `am force-stop` of anything other than the two Tideo packages.
3. **Registered mutations:**

   | Mutation | Guard | Restore / verification |
   |---|---|---|
   | `settings put system screen_brightness`, `screen_brightness_mode` | SettingsTxn | original value put back, read back |
   | `settings put global stay_on_while_plugged_in` (§11 32a only) | SettingsTxn | same |
   | Keys **Tideo itself writes**: secure `night_display_activated/_color_temperature/_auto_mode`, `accessibility_display_daltonizer(_enabled)`, `accessibility_display_inversion_enabled`, `doze_always_on`, `reduce_bright_colors_level/_activated`; global `stay_on_while_plugged_in`, `user_disabled_hdr_formats`, `are_user_disabled_hdr_formats_allowed`; prop `debug.hwui.force_dark` | snapshotted before any step that can trigger a Tideo write (PANIC, Apply, service start, profile swap) | `put` of the recorded original, read back. **A row that was absent cannot be restored** without `settings delete`, so every scenario that can create it (PANIC, Privileged Apply) **SKIPs at preflight** unless all its rows already exist. The prop is checked read-only; if force-dark opt-in is on, service-start scenarios SKIP |
   | `pm grant/revoke com.tideo.autobrightness.debug WRITE_SECURE_SETTINGS` | GrantTxn | original grant state. **Revoke kills the debug process.** The scenario expects death, relaunches through `am start` of the launcher activity, and re-primes service state from TideoPrefTxn |
   | Tideo prefs via UI (`serviceEnabled`, automation toggle, override detection, log level, Strength Setpoint, Privileged Display draft fields) | TideoPrefTxn: UI-visible value recorded before change, journaled | restored through the same UI, read back from the UI. PANIC's `serviceEnabled=false` is restored by `service_switch` |
   | `am broadcast … -n com.tideo.autobrightness.debug/com.tideo.autobrightness.app.control.ControlReceiver` | ≥1.5 s spacing (one-at-a-time gate) | — |
   | `input keyevent KEYCODE_SLEEP/WAKEUP` | — | awake at teardown |
   | `am force-stop com.tideo.autobrightness.debug` (§11 37 variants only) | explicit | relaunch + service state restore |
   | `am force-stop com.tideo.autobrightness` (release, owner-approved) | preflight, only if its FGS is running | at teardown `am start` its launcher activity so it leaves the stopped state (boot receiver re-armed). The report says whether its service came back and tells the owner if a manual re-enable is needed |
   | `install -r` debug APK | install guard | — |

4. **Transactions survive failure and interruption.**
   - The journal (`e2e/.state/journal.json`, gitignored) is written and fsync'd **before** each
     mutation.
   - Restore runs from `finally`, a session finalizer, and SIGINT/SIGTERM handlers.
   - A leftover journal is replayed and verified at the start of the next run, before anything else.
   - `run.sh --restore-only` replays it on demand.
   - Any read-back mismatch fails loudly.
5. **App-data preservation** (read-only).
   - Pre-run and post-run, `run-as com.tideo.autobrightness.debug` produces `tar c` of the **whole**
     data dir (`files`, `shared_prefs`, `no_backup`, `databases`, `cache` excluded) via
     `exec-out`. Output goes to the gitignored evidence directory, plus a sha256 manifest.
   - Post-run check:
     - every pre-run file still exists;
     - DataStore files are non-empty;
     - `firstInstallTime` is unchanged;
     - per-file hashes are listed, and expected churn (WorkManager db, `service_health`) is marked
       as such.
   - Logical Tideo prefs are verified by TideoPrefTxn read-back, not bytes.
   - The backup exists for **owner-driven** recovery only. The harness never writes app data back.
6. **Install guard**
   - Artifact identity: `aapt dump badging` confirms `com.tideo.autobrightness.debug`; record its
     sha256 and git commit.
   - The signing cert must equal the installed package's cert (apksigner on the pulled installed
     base.apk).
   - versionCode ≥ installed.
   - Before running, show the owner installed vs new (versionName, versionCode, commit) and **ask
     for approval**, because an equal-versionCode install overwrites the binary with no rollback.
   - `install -r` only, never `-d`/`-g`/uninstall.
   - On a cert mismatch: STOP and report.
   - Package snapshots model `lastUpdateTime`/`versionCode` as expected to change on install.
7. **Identifier hygiene.**
   - No Windows usernames, host paths, emails or device serials in any file under `e2e/`. Host docs
     use `$env:USERPROFILE`/`<your-user>` placeholders.
   - Evidence and command logs replace the device serial with `<serial>`.
   - A device-free unit test scans `e2e/` for `C:\Users\<literal>`, `/Users/<literal>`, email shapes
     and serial-looking tokens.
   - The ladder's secret scan still applies.
8. **Command audit log** (redacted): every command with its classification. It feeds the Sol review
   and the report.

## 4. Scenario conversion (`scenarios.toml`; initial targets)

**Auto**
- §0 tier badge readout.
- §1 4: switch on → FGS running + notification on `ambient_monitoring`.
- §2 8/9: injected write → pause oracle; `resume_button` → unpaused.
- §2 10a: 3 s quiet vs 5 s control.
- §2 10b: raw(d+1) quiet with fresh `DISMISSED_DRIFT`, raw(d+2) pauses. S is measured.
- §2 10c: mode 1 + far value → quiet, mode back to 0, fresh `DISMISSED_MODE`; control pauses.
- §2 10e: screen-off write → quiet on wake, followed by step 8 as the liveness proof.
- The DC-012 unpaused gate is checked before every half.
- §5 14a: PANIC via broadcast and via the notification Reset action → service stopped + brightness
  at S; serviceEnabled restored after. Requires the preflight row-presence check.
- §6 16: grant → ELEVATED badge after resume.
- §6 19b: grant while running → badge within ~10 s. Sol flagged that the Dashboard never calls
  `refreshTier()` (`DashboardScreen.kt:57`). If this fails, classify it from evidence as a Tideo
  defect vs an invalid assumption; never loosen the assert.
- §6 19a: Strength 100 → 65 + message; 64 and 65 give no message.
- §11 32a (stay-awake mask 0→15, 7 preserved + notice, "Use Tideo's setting" → 15) and 32c
  (read-only SKIP logic).
- §11 34 (service-OFF Apply writes the key; service start adopts without reverting).
- §11 36 (revoke → process death handled, Privileged row gone; re-grant).
- §11 39 (PANIC resets the display keys, then restore).
- §11 39b (Apply doesn't undo itself, service ON and OFF).
- §13 42–45 (gate, verbs, RESUME ignored when switched off, SERVICE_ON).

**Partial** (the harness automates setup and surrounding asserts; a human records the physical or
visual observation)
- §1 5–7, §2 10 (light).
- §3 11 (off/on readouts automated).
- §5 14/15/15a/15b (gesture; vibration).
- §6 17–19 (dark room).
- §9 29, once a concrete field + bound + persisted value is pinned from code in the test.
- §11 32: readback automated; the script requires visible device and Settings-UI agreement.
- §12 40/41: hierarchy label audit automated; uiautomator bounds ≠ Compose touch bounds, so the 48 dp
  check stays on-device.
- §13 44a/46 (flashes, outbound broadcast visibility).
- §14 47–50: IME/bar overlap geometry automated; clipping, doubled padding and 3-button mode stay
  visual.

**Manual** (reason recorded)
- §0 1–3 (fresh onboarding).
- §3 12, §15 54 (reboot not approved).
- §4 (proximity).
- §7 (fixed-location prefs).
- §8 (would create or delete user rules and profiles).
- §9 26–28.
- §10.
- §11 32b (needs `settings delete`), 32d, 33, 35, 37 (needs a context-loaded profile), 38, 39a,
  39c, 39d.
- §15 51–53, 55 (`setprop` denied).

## 5. Environment

- **Portable.** `e2e/bootstrap.sh` installs a pinned `uv` into `~/.local/bin` (the persistent home
  volume), then runs `uv sync --frozen`.
  - No apt packages and no Linux adb. adbutils speaks the server protocol to
    `host.docker.internal:5037`, configured by `ADB_SERVER_HOST`/`ADB_SERVER_PORT` in `run.sh`.
  - Versions come from `uv.lock`.
  - An optional owner Dockerfile snippet that bakes `uv` into the image is documented only. The
    compose files are outside the repo and not edited.
- **Host glue** (`e2e/host/`): Google platform-tools adb on Windows ARM64 (x64 under Prism).
  - First try the default server through Docker Desktop's `host.docker.internal` loopback proxy.
  - If refused, `start-adb-server.ps1` runs `adb -a nodaemon server start` plus a firewall rule
    allowing TCP 5037 only from the Docker/WSL vEthernet subnet.
  - The owner runs it.
- **Agent extra** (last phase):
  - `MOBILE_USE_TELEMETRY_ENABLED=false`, `ADB_HOST/PORT`.
  - Preflight fails if `dev.mobile.maestro` is installed, rather than letting mobile-use uninstall it.
  - It runs under the same transport audit hook.
  - The API key is read by the tool itself and never printed.
  - `run.sh --agent-triage <nodeid>` writes an advisory narrative into evidence; it never decides a
    result.
- **`.gitignore`**: `e2e/.state/`, `e2e/evidence/`, `e2e/.venv/`.

## 6. Execution order

1. **On acceptance:** create `docs/plans/DEVICE_E2E_PLAN.md` (this plan, identifiers-free) and
   commit **only that** on the current branch, following the AMH commit-body rules. No other change
   in that commit.
2. Wait for the owner before continuing past the commit if they say so. Otherwise branch
   `claude/device-e2e` from it (branch-train), and ask before any push.
3. Ledger row (decision + safety model). Then the `e2e/` safety core + device-free unit tests, with
   sol-review beside the writes.
4. Testability hook + Robolectric test. Then scenarios + `scenarios.toml`.
5. `scripts/ladder.sh` green, including `:app:assembleDebug` on the builder.
6. **Blocking Sol review before device contact.** Give it the full `device.py`/`safety.py`/`txn.py`/
   `conftest.py`, the mutation table, the install guard, every mutating test, and the hazard lines.
   Fix safety findings first.
7. Owner starts the host adb server. Run read-only `run.sh --preflight`:
   - connectivity;
   - package, variant and cert;
   - release FGS state;
   - key snapshot + row presence;
   - force-dark opt-in;
   - app-data backup.
8. Owner approves the install; install via the guard. Run `-m smoke`: service on/off, tags visible
   as resource-ids, one SettingsTxn round trip.
9. Sections from least to most state-heavy: §13 → §2 → §6 → §11 read paths → §5 PANIC → §11 PANIC
   and revoke. After each, the journal must be empty and `--verify-restored` clean.
10. For each failure, classify it (Tideo defect / harness / framework / timing / OEM / invalid
    assumption) from evidence. Fix in scope, rerun the node, then the section.
11. Agent extra (optional phase), smoke-tested on one triage run.
12. Final checks:
    - ladder green;
    - full auto suite;
    - restoration report;
    - app-data manifest check;
    - `git diff` review, including the identifier scan;
    - final Sol review on safety and on whether each test can fail on a broken build.
    Then STATE.md, DEVICE_TEST_SCRIPT pointers and a RUNBOOK playbook for the device suite.

## Verification

- **Device-free:** `e2e/run.sh pytest tests/unit` covers the classifier with evasion variants,
  journal replay, restore-on-exception, conversion maths, manifest completeness and the identifier
  scan. Also run `scripts/ladder.sh`.
- **Device:** `run.sh --preflight` → `-m smoke` → `run.sh` → `run.sh --verify-restored`. Record
  JUnit XML + the evidence directory.
- **Report:**
  - the decision;
  - the architecture;
  - the auto/partial/manual table;
  - results;
  - defects and fixes;
  - dependencies (from `uv.lock`);
  - limitations;
  - restoration confirmation;
  - what touched the phone (the audit log).
