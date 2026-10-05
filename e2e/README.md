# Tideo real-device E2E suite

An executable twin of `docs/rebuild/DEVICE_TEST_SCRIPT.md`, run against a real phone through the
host's adb server. The Markdown script stays the human spec; `scenarios.toml` holds one row per
script step, classified `auto`, `partial` or `manual` with a reason. Decision and safety model:
DD-015.

**Status: boundary, recovery, scenarios, install guard and read-only preflight built (S5,
DD-029); the smoke set passes on a device (S6, DD-032); the effect-ordered suites have run
there (S7, DD-033), with their open items in `docs/STATE.md`.**

## Safety boundary

- **Commands** (`tideo_e2e/device.py`). Importing it gates every adb service request at
  adbutils' socket chokepoint, so library internals are judged like harness calls; the `adb`
  binary is never run. A shell command must clear a hard denylist, be in canonical `shlex` form
  (no shell operators reach the device) and match an exact typed template. Templates are graded
  READ, HARNESS (uiautomator2's u2.jar push and server) and MUTATE; a session admits READ only
  unless opened otherwise. With no session open, nothing is sent.
- **UI** (`tideo_e2e/ui.py`). Scenario code acts only by naming a `Target` in its `UiAllowlist`,
  which holds the reads plus the controls its row's effects cover. Each action resolves exactly
  one node of the declared package, checks that this package holds input focus (read from
  `dumpsys window displays`; unknown focus refuses), a global denylist (profile
  delete/overwrite/load/restore-factory, import/export, rule editors, calibration) and that no
  other package's node covers the tap point, then taps that node. A shade action needs the
  `NotificationShade` window focused and Tideo's label in the row's app-name header; a collapsed
  row is expanded once per opening, and only when the unredacted notification dump shows the
  debug package alone posted its title. The shade closes by `cmd statusbar collapse`, never Back.
  A target off screen is swiped into view only through the package's own scrollable in the dump.
  uiautomator2's RPCs are gated too: reads pass, a click or one swipe only as just authorised.
  Its install/IME/shell conveniences raise (DD-033).
- **Not a sandbox.** The gates confine library internals and harness code using public APIs. Code
  that writes to a raw adb socket, calls the saved originals or spawns a process would pass
  around them; `test_boundary_tripwire.py` fails if harness code outside the boundary modules
  does any of that.
- **Effects** (`tideo_e2e/effects.py`). Each effect kind a scenario declares maps to the settings
  keys, grant, prefs and runtime state it may change, directly or through Tideo, plus a run
  stage (settings → service/wake → grant → revoke/force-stop → PANIC) and the preflight SKIP
  rules.
- **Journal** (`tideo_e2e/journal.py`), in the private store `${XDG_STATE_HOME:-~/.local/state}/
  tideo-e2e/` (0700), never under `e2e/`. Bound to a salted serial digest, user and package
  identity; one run or recovery at a time (flock). Each entry is fsync'd before the mutation it
  covers, holding the original and the values the run may have left: the last one observed and
  the one about to be written. It also holds the curve points an override scenario found. A
  journal on disk blocks the next run and any install.
- **Recovery** (`tideo_e2e/recovery.py`), one procedure for teardown and `--recover`: wake the
  screen, quiesce the service (serviceEnabled too), restore the grant, restore settings, then
  prefs, verify twice a settle window apart, restore the runtime state, then check the curve
  points. Under a service that was running, the brightness mode is left to Tideo's restart and
  the owner's saved mode is checked instead. A key holding a value the run did not cause is a
  **conflict**: never written, kept in the journal and reported until it is back at its original
  or the owner resolves it. Unit tests kill it at every call and every journal write and rerun it.
- **Device port** (`tideo_e2e/state.py`, `tideo_e2e/tideo.py`). What recovery runs on: read-only
  parsers for settings, `dumpsys` and Tideo's private stores, and the verbs. The service is
  switched through the Dashboard's `service_switch`, and only once it shows the stored
  `serviceEnabled`; a broadcast would need external control on. Paused is the Resume action on
  Tideo's ongoing notification. Only Android user 0 is supported. A preference is restorable only
  through a registered UI routine, so a scenario must not journal one that has none.
- **Scenarios** (`tests/test_s*.py`, runtime `tideo_e2e/harness.py`, fixture
  `tests/conftest.py`). A test is marked with its row id and gets a `Run`, which refuses an
  action whose effect kind the row does not declare, a setting outside the footprint, and a
  control whose preferences are not journaled. Every Privileged Display preference the screen
  reads back is journaled before the first tap, and the screen must open clean. The fixture
  applies the SKIP rules, journals the footprint, and recovers after the test, pass or fail;
  `test_harness.py` also checks each scenario's calls against its row statically. An injected
  override's curve point is removed with the notification's Discard (owner, 2026-10-02); points
  that differ afterwards are a recovery conflict, and at the 50-point cap §2 SKIPs. A scenario
  reaching past `Run` to its UI, port or journal fails `test_harness.py`.
- **Install guard** (`tideo_e2e/install.py`). Empty journal; debug package; the one signer's
  SHA-256 equal to the installed `base.apk`'s (apksigner); versionCode not lower; the owner types
  `INSTALL`. Then one streamed `cmd package install -r` of the inspected bytes, never -d, -g, -t
  or an uninstall. Only when `dumpsys package` says it cannot find the debug package is an
  install a first one: the APK's single signer is then the one every later install must match. Needs `JAVA_HOME` and the Android SDK (`ANDROID_HOME` or `local.properties`).
- **Drift alarm** (`tideo_e2e/drift.py`). A pin bump that changes any device-facing line of
  adbutils or uiautomator2 fails `test_dependency_drift.py` until the new lines are checked.

## Setup

```
e2e/bootstrap.sh      # pinned, checksum-verified uv into ~/.local/bin; uv sync --frozen
```

No apt packages and no Linux adb are needed. Dependencies are exact pins in `pyproject.toml`,
resolved in `uv.lock`.

## Run

```
e2e/run.sh tests/unit          # device-free: manifest, identifiers, boundary, drift
TIDEO_E2E_SERIAL=<serial> e2e/run.sh --preflight     # strictly read-only; run first
TIDEO_E2E_SERIAL=<serial> e2e/run.sh -k s02_10b      # one scenario
TIDEO_E2E_SERIAL=<serial> e2e/run.sh -m smoke        # S6's smoke set
TIDEO_E2E_SERIAL=<serial> e2e/run.sh --recover       # restore what an interrupted run left
TIDEO_E2E_SERIAL=<serial> e2e/run.sh --install <apk> # guarded install of a debug build
```

Without `TIDEO_E2E_SERIAL` every scenario SKIPs. `TIDEO_E2E_CONFIRM=contexts,automation` records
the owner's confirmations for the SKIP rules. Wake scenarios (§2 10a, 10e) need the phone to
wake unlocked; otherwise they stop, and `--recover` runs once it is unlocked.

`--recover` exits 0 at once on an empty journal. Otherwise it checks the device's identity over
a read-only session before anything else, then runs the recovery procedure. Exit 1 means
conflicts or a stop, and the journal keeps what is left. The adb server defaults to
`host.docker.internal:5037` (`ADB_SERVER_HOST`, `ADB_SERVER_PORT`). A phone in wireless
`adb tcpip` mode can instead be reached from a server inside the container: `adb connect
<phone>:5555`, then `ADB_SERVER_HOST=127.0.0.1`. The serial is then `<phone>:5555`, which stays
in the environment, never in a file.

`--preflight` refuses on a pending journal, a missing device profile or debug build, a
brightness above the profile's S, or a language list through which Tideo might render Chinese
(the suite reads English text). It prints the snapshot summary and which scenarios the SKIP
rules would skip, and writes the full snapshot and an archive of Tideo's data dir (read with
`run-as … tar`) to the private store only. Exit 0 means a run may start.

## Rules for this directory

- **Lockstep with the script.** Adding, removing or rewriting a script step updates its
  `scenarios.toml` row in the same commit (RUNBOOK playbook 5). `tests/unit/test_manifest.py`
  fails on a step without a row, an undeclared effect kind, a malformed field, or an `auto` row
  that neither names a test pytest collects under `tests/` nor is marked `pending = true`
  (scenario not written yet).
- **No personal identifiers** in any file here: no usernames, emails, device serials, adb
  addresses or SSIDs. Write placeholders as `<user>`, `<serial>`. `tests/unit/test_identifiers.py`
  scans the tree for those shapes; it is a tripwire, not proof.
- **Raw captures never land under `e2e/`.** Only sanitised text may go to `e2e/evidence/`
  (gitignored).
