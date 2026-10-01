# Tideo real-device E2E suite

An executable twin of `docs/rebuild/DEVICE_TEST_SCRIPT.md`, run against a real phone through the
host's adb server. The Markdown script stays the human spec; `scenarios.toml` holds one row per
script step, classified `auto`, `partial` or `manual` with a reason. Decision and safety model:
DD-015.

**Status: boundary built, no device contact yet.** The recovery journal and the scenarios arrive
in later segments. No device mutation is authorised until the recovery contract is implemented,
interruption-tested and reviewed.

## Safety boundary

- **Commands** (`tideo_e2e/device.py`). Importing it gates every adb service request at
  adbutils' socket chokepoint, so library internals are judged like harness calls; the `adb`
  binary is never run. A shell command must clear a hard denylist, be in canonical `shlex` form
  (no shell operators reach the device) and match an exact typed template. Templates are graded
  READ, HARNESS (uiautomator2's u2.jar push and server) and MUTATE; a session admits READ only
  unless opened otherwise. With no session open, nothing is sent.
- **UI** (`tideo_e2e/ui.py`). Scenario code acts only by naming a `Target` in its `UiAllowlist`.
  Each action resolves exactly one node of the declared package, checks the foreground and a
  global denylist (profile delete/overwrite/load/restore-factory, import/export, rule editors,
  calibration), then taps that node. uiautomator2's RPCs are gated too: reads pass, a click only
  at the point just authorised. Its install/IME/shell conveniences raise.
- **Not a sandbox.** The gates confine library internals and harness code using public APIs. Code
  that writes to a raw adb socket, calls the saved originals or spawns a process would pass
  around them; `test_boundary_tripwire.py` fails if harness code outside the boundary modules
  does any of that.
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
e2e/run.sh -k s02_10b          # one scenario (once scenarios exist)
```

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
