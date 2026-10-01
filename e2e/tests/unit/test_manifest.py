"""scenarios.toml stays in lockstep with DEVICE_TEST_SCRIPT and with the tests that exist."""

import subprocess
import sys
from collections import Counter

import pytest

from tideo_e2e.scenarios import (
    E2E_ROOT, EFFECTS, STATUSES, ManifestError, load, script_step_ids,
)

ROWS = load()
STEPS = script_step_ids()


def test_script_parses_to_steps():
    # Guards the extractor itself: §0 starts at step 1 and §2 carries the 10a…10e family.
    assert STEPS[:3] == ["s00_1", "s00_2", "s00_3"]
    assert {"s02_10a", "s02_10e", "s11_39d", "s13_44a"} <= set(STEPS)


def test_ids_unique():
    dupes = [i for i, n in Counter(r.id for r in ROWS).items() if n > 1]
    assert not dupes


def test_every_script_step_has_a_row():
    assert sorted(set(STEPS) - {r.id for r in ROWS}) == []


def test_non_extra_rows_name_a_script_step():
    assert [r.id for r in ROWS if not r.extra and r.id not in STEPS] == []


def test_extra_rows_do_not_shadow_steps():
    assert [r.id for r in ROWS if r.extra and r.id in STEPS] == []


@pytest.mark.parametrize("row", ROWS, ids=lambda r: r.id)
def test_row_is_well_formed(row):
    assert not row.unknown, f"unknown fields {row.unknown}"
    assert row.status in STATUSES
    assert set(row.effects) <= EFFECTS, f"unknown effects {set(row.effects) - EFFECTS}"
    if row.status == "manual":
        assert row.reason and not row.effects and not row.test
    else:
        assert row.effects, "auto/partial rows declare their effects"
        assert not ("read_only" in row.effects and len(row.effects) > 1)
    if row.status == "partial":
        assert row.reason
    if row.status == "auto":
        assert row.test or row.pending, "auto row points at nothing"
        assert not (row.test and row.pending)
    else:
        assert not row.pending


def _collects(nodeid: str) -> bool:
    """True when pytest collects at least one test item at exactly this node id."""
    if not nodeid.startswith("tests/") or nodeid.startswith("tests/unit/") or "::" not in nodeid:
        return False
    run = subprocess.run(
        [sys.executable, "-m", "pytest", "--collect-only", "-q", "-p", "no:cacheprovider", nodeid],
        cwd=E2E_ROOT, capture_output=True, text=True,
    )
    return run.returncode == 0 and any(
        line == nodeid or line.startswith(nodeid + "[") for line in run.stdout.splitlines()
    )


@pytest.mark.parametrize("row", [r for r in ROWS if r.test], ids=lambda r: r.id)
def test_named_test_collects(row):
    assert _collects(row.test), f"{row.test} does not collect as a scenario test"


def test_collect_check_rejects_non_scenarios():
    assert not _collects("tests/unit/test_manifest.py::test_ids_unique")
    assert not _collects("README.md")
    assert not _collects("tests/test_absent.py::test_x")


@pytest.mark.parametrize("body", [
    'stray = 1\n[[scenario]]\nid = "s00_1"\nstatus = "manual"\nreason = "r"\n',
    '[[scenario]]\nid = "s01_4"\nstatus = "auto"\neffects = ["ui_nav"]\npending = "false"\n',
    '[[scenario]]\nid = "s00_1"\nstatus = "manual"\nreason = " "\n',
    '[[scenario]]\nid = "s01_4"\nstatus = "auto"\neffects = [1]\npending = true\n',
])
def test_loader_rejects_malformed(tmp_path, body):
    path = tmp_path / "scenarios.toml"
    path.write_text(body)
    with pytest.raises(ManifestError):
        load(path)
