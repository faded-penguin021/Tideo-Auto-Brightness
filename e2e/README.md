# Tideo real-device E2E suite

An executable twin of `docs/rebuild/DEVICE_TEST_SCRIPT.md`, run against a real phone through the
host's adb server. The Markdown script stays the human spec; `scenarios.toml` holds one row per
script step, classified `auto`, `partial` or `manual` with a reason. Decision and safety model:
DD-015.

**Status: scaffolding only.** Nothing here contacts a device yet. The command and UI boundary,
the recovery journal and the scenarios arrive in later segments. No device mutation is
authorised until the recovery contract is implemented, interruption-tested and reviewed.

## Setup

```
e2e/bootstrap.sh      # pinned, checksum-verified uv into ~/.local/bin; uv sync --frozen
```

No apt packages and no Linux adb are needed. Dependencies are exact pins in `pyproject.toml`,
resolved in `uv.lock`.

## Run

```
e2e/run.sh tests/unit          # device-free: manifest lockstep, identifier scan
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
