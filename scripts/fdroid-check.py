#!/usr/bin/env python3
"""F-Droid compatibility checks that official tooling does not cover (DA-027).

Three subcommands, each backing one stage of `.github/workflows/fdroid-compat.yml`.
Everything here is stdlib-only and runs offline except `metadata`, so a maintainer can
reproduce any CI failure locally with the same command the workflow ran.

    compare A.apk B.apk     reproducibility: same source built in two environments
    signing-blocks APK      no unexpected APK Signing Block IDs (D-137)
    metadata                repo still satisfies the live fdroiddata recipe

This deliberately does NOT reimplement fdroidserver. `fdroid build` and `fdroid scanner`
are run in CI as the official tools they are; these are the gaps around them:
fdroidserver has no "did these two builds match" check that works before a release
exists, and no check that our own repo still fits the recipe upstream holds for us.

Why the checks are shaped the way they are is documented in
`docs/rebuild/FDROID_VALIDATION.md` — read that before changing behavior here.
"""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

# The fdroiddata recipe that upstream actually builds this app with. Fetched live rather
# than vendored: a stale copy in-repo would drift silently and assert yesterday's truth.
RECIPE_URL = (
    "https://gitlab.com/fdroid/fdroiddata/-/raw/master/metadata/com.tideo.autobrightness.yml"
)

# APK Signing Block IDs that a legitimately signed release carries. Anything else means
# something injected a blob into the signing block — the D-137 case is AGP's Play
# dependency-metadata blob (0x504b4453), which is unreadable by anyone but Google Play and
# is THE standard obstacle to a byte-identical F-Droid rebuild. `dependenciesInfo { ... =
# false }` in app/build.gradle.kts turns it off; this check is what notices if that ever
# gets reverted. Verified against a deliberately-regressed build, not assumed (DA-027).
KNOWN_SIGNING_BLOCK_IDS = {
    0x7109871A: "APK Signature Scheme v2",
    0xF05368C0: "APK Signature Scheme v3",
    0x1B93AD61: "APK Signature Scheme v3.1",
    0x42726577: "verity padding block",
    0x6DFF800D: "source stamp",
}

# Entries whose bytes legitimately differ between two independent builds of the same
# source: the v1/JAR signature files, which exist only if the APK was signed, and only
# describe the signature. Everything else must match. (v2/v3 signatures live in the
# signing block, outside the zip, so they never show up in this comparison at all.)
SIGNATURE_ENTRY_RE = re.compile(r"^META-INF/[^/]+\.(SF|RSA|DSA|EC)$|^META-INF/MANIFEST\.MF$")


def _fail(stage: str, message: str) -> int:
    """Emit a GitHub annotation naming the stage, plus a plain line for local runs."""
    print(f"::error title={stage}::{message}")
    print(f"\n{stage}: {message}", file=sys.stderr)
    return 1


def _ok(message: str) -> int:
    print(f"OK: {message}")
    return 0


# --------------------------------------------------------------------------------------
# compare — reproducibility
# --------------------------------------------------------------------------------------


def _content_map(apk: Path) -> dict[str, list[int]]:
    """Map every non-signature zip entry name to the CRC(s) stored under it.

    Per-entry CRC32, not raw bytes: this compares entry *contents* independent of zip framing
    (entry order, alignment, timestamps), which is the level F-Droid's own reproducible-build
    comparison works at — so this stage does not fail on a difference F-Droid would forgive.
    A CRC32 is a checksum, not a hash: it proves difference reliably and sameness only to
    within a collision, which is the right trade for a change-detector.

    The value is a LIST because a zip may legitimately carry duplicate entry names, and a
    duplicate appearing in one APK but not the other is exactly the packaging hazard worth
    catching — a dict keyed on filename would silently keep only the last one.
    """
    out: dict[str, list[int]] = {}
    with zipfile.ZipFile(apk) as zf:
        for info in zf.infolist():
            if not SIGNATURE_ENTRY_RE.match(info.filename):
                out.setdefault(info.filename, []).append(info.CRC)
    for crcs in out.values():
        crcs.sort()
    return out


def cmd_compare(args: argparse.Namespace) -> int:
    a, b = Path(args.apk_a), Path(args.apk_b)
    for p in (a, b):
        if not p.is_file():
            return _fail("Reproducibility validation failed", f"APK not found: {p}")

    ma, mb = _content_map(a), _content_map(b)
    only_a = sorted(set(ma) - set(mb))
    only_b = sorted(set(mb) - set(ma))
    changed = sorted(k for k in set(ma) & set(mb) if ma[k] != mb[k])

    report = {
        "apk_a": str(a),
        "apk_b": str(b),
        "entries_a": len(ma),
        "entries_b": len(mb),
        "only_in_a": only_a,
        "only_in_b": only_b,
        "content_differs": changed,
        "identical": not (only_a or only_b or changed),
    }
    if args.report:
        Path(args.report).write_text(json.dumps(report, indent=2) + "\n")

    if report["identical"]:
        return _ok(
            f"{len(ma)} entries identical between {a.name} and {b.name} "
            "(signature files excluded) — the two builds reproduce each other"
        )

    for name in only_a:
        print(f"  only in {a.name}: {name}")
    for name in only_b:
        print(f"  only in {b.name}: {name}")
    for name in changed:
        print(f"  differs: {name}")
    return _fail(
        "Reproducibility validation failed",
        f"{len(only_a) + len(only_b) + len(changed)} entries differ between the normal "
        f"release build and the F-Droid build of the same commit. F-Droid rebuilds every "
        f"release from source and publishes it only if it matches; a difference here is a "
        f"difference there. See the uploaded reproducibility report and diffoscope diff.",
    )


# --------------------------------------------------------------------------------------
# signing-blocks — D-137
# --------------------------------------------------------------------------------------


def _signing_block_ids(apk: Path) -> list[int] | None:
    """Return the APK Signing Block IDs, or None if the APK is unsigned.

    Layout (Android docs, "APK Signing Block"): the block sits between the zip entries and
    the central directory, framed by an 8-byte size at each end and the magic
    "APK Sig Block 42"; inside it is a sequence of length-prefixed ID/value pairs.
    """
    data = apk.read_bytes()
    magic = data.rfind(b"APK Sig Block 42")
    if magic < 0:
        return None
    size_end = struct.unpack("<Q", data[magic - 8 : magic])[0]
    pos = magic + 16 - 8 - size_end + 8  # skip the leading size field
    end = magic - 8
    ids: list[int] = []
    while pos < end:
        length = struct.unpack("<Q", data[pos : pos + 8])[0]
        if length < 4 or pos + 8 + length > end + 8:
            # Do NOT return what was parsed so far (DA-028): unknown IDs could sit past this point, and
            # reporting a partial list as complete would fail open on the tampered case.
            raise ValueError(
                f"malformed APK Signing Block at offset {pos}: pair length {length} does not fit"
            )
        ids.append(struct.unpack("<I", data[pos + 8 : pos + 12])[0])
        pos += 8 + length
    return ids


def cmd_signing_blocks(args: argparse.Namespace) -> int:
    apk = Path(args.apk)
    if not apk.is_file():
        return _fail("Signing assumption check failed", f"APK not found: {apk}")

    try:
        ids = _signing_block_ids(apk)
    except ValueError as exc:
        return _fail("Signing assumption check failed", f"{apk.name}: {exc}")
    if ids is None:
        # Not a failure by itself: an unsigned APK simply has nothing to check. The
        # workflow only points this at the signed build, so say so rather than pass mutely.
        return _ok(f"{apk.name} is unsigned — no signing block to inspect")

    unknown = [i for i in ids if i not in KNOWN_SIGNING_BLOCK_IDS]
    for i in ids:
        print(f"  0x{i:08x}  {KNOWN_SIGNING_BLOCK_IDS.get(i, 'UNKNOWN')}")
    if not unknown:
        return _ok(f"{apk.name}: only known signing-block IDs present")

    detail = ", ".join(f"0x{i:08x}" for i in unknown)
    hint = ""
    if 0x504B4453 in unknown:
        hint = (
            " 0x504b4453 is AGP's Google Play dependency-metadata blob — set "
            "`dependenciesInfo { includeInApk = false; includeInBundle = false }` in "
            "app/build.gradle.kts (D-137). It is encrypted to Play, useless to F-Droid, "
            "and blocks byte-identical rebuilds."
        )
    return _fail(
        "Signing assumption check failed",
        f"unexpected APK Signing Block ID(s): {detail}.{hint}",
    )


# --------------------------------------------------------------------------------------
# metadata — the recipe upstream builds us with
# --------------------------------------------------------------------------------------


def _recipe_value(recipe: str, key: str) -> str | None:
    """Read a top-level scalar from the recipe without a YAML dependency."""
    m = re.search(rf"^{re.escape(key)}:\s*(.+?)\s*$", recipe, re.M)
    return m.group(1) if m else None


def cmd_metadata(args: argparse.Namespace) -> int:
    try:
        with urllib.request.urlopen(RECIPE_URL, timeout=30) as resp:
            recipe = resp.read().decode()
    except urllib.error.HTTPError as exc:
        # The server answered, and the answer was "no". A 404 means the recipe moved, was
        # renamed, or the app was dropped from fdroiddata — all real, all things a release
        # needs to know about. Never treat this as an outage.
        return _fail(
            "Metadata validation failed",
            f"fdroiddata returned HTTP {exc.code} for {RECIPE_URL}. The recipe may have moved or "
            "the app may no longer be in fdroiddata — check the package page before releasing.",
        )
    except Exception as exc:  # noqa: BLE001 — genuinely could not reach the server
        # Only a transport-level failure gets the free pass: a gate that fails on someone
        # else's outage teaches contributors to ignore it. Anything the server *answered*
        # is handled above.
        print(f"::warning title=Metadata validation skipped::could not reach {RECIPE_URL}: {exc}")
        return 0

    problems: list[str] = []
    app_gradle = (REPO_ROOT / "app" / "build.gradle.kts").read_text()

    # A key we cannot parse is a FAILURE, not a skip (DA-028). Silently degrading to "checked nothing,
    # reported success" is the one outcome this whole subcommand exists to prevent: it would
    # keep printing "satisfies the recipe" long after the recipe stopped being understood.
    def required(value: str | None, key: str) -> str | None:
        if not value:
            problems.append(
                f"could not read '{key}' from the recipe — its shape changed, so the checks "
                "that depend on it did NOT run (fix this script against the current recipe)"
            )
            return None
        return value

    # 1. Source layout: the recipe builds `subdir: app`.
    subdir = required(
        _recipe_value(recipe, "  - subdir") or _recipe_value(recipe, "    subdir"), "subdir"
    )
    if subdir and not (REPO_ROOT / subdir / "build.gradle.kts").is_file():
        problems.append(
            f"the recipe builds subdir '{subdir}', but {subdir}/build.gradle.kts does not exist"
        )

    # 2. Release asset name: reproducible-build mode fetches our GitHub asset by exact
    #    filename, so renaming it in release.yml would 404 upstream.
    binaries = _recipe_value(recipe, "Binaries") or ""
    if not binaries:
        m = re.search(r"^Binaries:\s*\n\s*(\S+)\s*$", recipe, re.M)
        binaries = m.group(1) if m else ""
    binaries = required(binaries, "Binaries") or ""
    if binaries:
        asset = binaries.rsplit("/", 1)[-1]
        release_yml = (REPO_ROOT / ".github" / "workflows" / "release.yml").read_text()
        # Substring over the whole file: deliberately loose. The asset name appears in several
        # steps and pinning this to one of them would break on an innocuous refactor; a rename
        # that misses EVERY mention is the failure worth catching.
        if asset not in release_yml:
            problems.append(
                f"the recipe downloads the release asset '{asset}', which release.yml no "
                "longer produces under that name (reproducible-build mode would 404)"
            )
        if "/v%v/" not in binaries:
            problems.append(
                f"the recipe's Binaries URL no longer uses the v%v tag form: {binaries}"
            )

    # 3. UpdateCheckMode: Tags + AutoUpdateMode: Version means our tag is v<versionName>,
    #    so versionName must stay a plain dotted release version.
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', app_gradle)
    if not version_name:
        problems.append("could not read versionName from app/build.gradle.kts")
    elif not re.fullmatch(r"\d+\.\d+\.\d+", version_name.group(1)):
        problems.append(
            f"versionName '{version_name.group(1)}' is not the plain X.Y.Z form the "
            "recipe's UpdateCheckMode: Tags / AutoUpdateMode: Version rely on"
        )

    # 4. versionCode must never move backwards past what upstream already published.
    current_raw = required(_recipe_value(recipe, "CurrentVersionCode"), "CurrentVersionCode")
    current_digits = re.match(r"\s*(\d+)", current_raw or "")   # tolerate a trailing comment
    current_code = current_digits.group(1) if current_digits else None
    if current_raw and not current_code:
        problems.append(f"CurrentVersionCode in the recipe is not a number: {current_raw!r}")
    version_code = re.search(r"versionCode\s*=\s*(\d+)", app_gradle)
    if not version_code:
        problems.append("could not read versionCode from app/build.gradle.kts")
    if current_code and version_code and int(version_code.group(1)) < int(current_code):
        problems.append(
            f"versionCode {version_code.group(1)} is lower than the published "
            f"CurrentVersionCode {current_code} — F-Droid rejects a non-monotonic code"
        )

    # 5. `gradle: [yes]` is the no-flavor build. A product flavor would make upstream build
    #    a variant nobody chose.
    if "productFlavors" in app_gradle:
        problems.append(
            "app/build.gradle.kts declares productFlavors, but the recipe builds the "
            "flavorless `gradle: - yes` variant — upstream would build the wrong APK"
        )

    if problems:
        for p in problems:
            print(f"  - {p}")
        return _fail(
            "Metadata validation failed",
            "the repository no longer satisfies the fdroiddata recipe upstream builds it "
            f"with ({len(problems)} problem(s) above). Fixing this repo is usually right; "
            "if the recipe itself should change, that is a merge request against fdroiddata.",
        )
    return _ok("repository still satisfies the live fdroiddata recipe")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)

    p_cmp = sub.add_parser("compare", help="compare two independently built APKs")
    p_cmp.add_argument("apk_a")
    p_cmp.add_argument("apk_b")
    p_cmp.add_argument("--report", help="write a JSON report here")
    p_cmp.set_defaults(func=cmd_compare)

    p_sig = sub.add_parser("signing-blocks", help="check for unexpected signing-block IDs")
    p_sig.add_argument("apk")
    p_sig.set_defaults(func=cmd_signing_blocks)

    p_meta = sub.add_parser("metadata", help="check the repo against the live fdroiddata recipe")
    p_meta.set_defaults(func=cmd_metadata)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
