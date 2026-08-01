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
import hashlib
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


def _content_map(apk: Path) -> dict[str, list[str]]:
    """Map every non-signature zip entry name to the SHA-256 of its DECOMPRESSED bytes.

    DB-003: this used to read `ZipInfo.CRC` — the checksum the archive *declares* — and never
    decompressed anything. That trusts metadata to describe payload: an entry whose bytes were
    changed while its CRC field was left alone compared equal, which the repo's own selftest now
    demonstrates. It is also a 32-bit checksum being asked to stand in for content identity.

    Hashing the decompressed bytes fixes both: it is the entry's actual content, at collision
    resistance that makes "equal hash, different bytes" not worth reasoning about. Reading the bytes
    also means a corrupt entry raises instead of silently comparing equal.

    Still deliberately *not* a byte-for-byte archive comparison: zip framing (entry order, alignment,
    extra fields, timestamps) is excluded, because two honest builds of one commit may legitimately
    differ there. See FDROID_VALIDATION.md for what this is and is not evidence of.
    """
    out: dict[str, list[str]] = {}
    with zipfile.ZipFile(apk) as zf:
        for info in zf.infolist():
            if info.is_dir() or SIGNATURE_ENTRY_RE.match(info.filename):
                continue
            digest = hashlib.sha256()
            with zf.open(info) as entry:
                for chunk in iter(lambda: entry.read(1024 * 1024), b""):
                    digest.update(chunk)
            out.setdefault(info.filename, []).append(digest.hexdigest())
    for digests in out.values():
        digests.sort()
    return out


def cmd_compare(args: argparse.Namespace) -> int:
    a, b = Path(args.apk_a), Path(args.apk_b)
    for p in (a, b):
        if not p.is_file():
            return _fail("Reproducibility validation failed", f"APK not found: {p}")

    try:
        ma, mb = _content_map(a), _content_map(b)
    except (zipfile.BadZipFile, OSError) as exc:
        # A corrupt entry is a reproducibility failure with a clearer cause than "these differ" —
        # and reading the bytes is what surfaces it at all (the old CRC-metadata comparison could
        # not have noticed). Report it as the stage failure it is, not as a traceback.
        return _fail(
            "Reproducibility validation failed",
            f"an APK could not be read as a valid archive: {exc}",
        )
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
            f"{len(ma)} entries match by SHA-256 of their decompressed bytes between {a.name} "
            f"and {b.name} (signature files excluded) — the two builds agree on content"
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

    DB-003: located by STRUCTURE, not by search. The previous version did `data.rfind(b"APK Sig
    Block 42")` over the whole file, so any occurrence of those 16 bytes inside an entry's payload
    could be mistaken for the block header — an attacker-supplied string deciding where a security
    check starts reading. It also allowed a pair to overrun the block by eight bytes
    (`pos + 8 + length > end + 8`), i.e. the last pair could claim length past the region.

    The real layout (Android "APK Signing Block"): [zip entries][APK Signing Block][central
    directory][EOCD]. The EOCD records where the central directory starts, and the signing block is
    the region ending immediately before it, framed by an 8-byte size at each end plus the magic. So
    walk EOCD → central-directory offset → the 24 bytes before it, and verify the magic *there*.
    """
    data = apk.read_bytes()
    cd_offset = _central_directory_offset(data)
    if cd_offset is None or cd_offset < 24:
        return None
    # The 8 bytes at [cd_offset-24, cd_offset-16) are the trailing size; the 16 after it, the magic.
    if data[cd_offset - 16 : cd_offset] != b"APK Sig Block 42":
        return None  # no signing block: an unsigned (or v1-only) APK
    size_end = struct.unpack("<Q", data[cd_offset - 24 : cd_offset - 16])[0]
    block_start = cd_offset - 8 - size_end  # start of the leading size field
    if block_start < 0 or block_start + 8 > len(data):
        raise ValueError("APK Signing Block size field does not fit the file")
    size_start = struct.unpack("<Q", data[block_start : block_start + 8])[0]
    if size_start != size_end:
        raise ValueError(f"APK Signing Block size mismatch: {size_start} vs {size_end}")

    pos = block_start + 8            # first ID/value pair
    end = cd_offset - 24             # first byte past the last pair
    ids: list[int] = []
    while pos < end:
        if pos + 8 > end:
            raise ValueError(f"truncated pair length field at offset {pos}")
        length = struct.unpack("<Q", data[pos : pos + 8])[0]
        # A pair is [8-byte length][4-byte id][value]; length counts id+value, so it must be >= 4 and
        # must not run past the pairs region. `> end` — not `> end + 8`, which tolerated an overrun.
        if length < 4 or pos + 8 + length > end:
            raise ValueError(
                f"malformed APK Signing Block at offset {pos}: pair length {length} does not fit"
            )
        ids.append(struct.unpack("<I", data[pos + 8 : pos + 12])[0])
        pos += 8 + length
    if pos != end:
        raise ValueError(f"APK Signing Block pairs end at {pos}, expected {end}")
    return ids


def _central_directory_offset(data: bytes) -> int | None:
    """Offset of the zip central directory, read from the End Of Central Directory record."""
    # EOCD is 22 bytes plus a comment of up to 0xFFFF; scan back from the end for its signature.
    max_back = min(len(data), 22 + 0xFFFF)
    window = data[len(data) - max_back :]
    idx = window.rfind(b"PK\x05\x06")
    if idx < 0:
        return None
    eocd = len(data) - max_back + idx
    if eocd + 20 > len(data):
        return None
    offset = struct.unpack("<I", data[eocd + 16 : eocd + 20])[0]
    if offset == 0xFFFFFFFF:
        # ZIP64: the real offset lives in the ZIP64 EOCD record. APKs this large are not a thing
        # here, and guessing would be worse than declining.
        raise ValueError("ZIP64 archives are not supported by the signing-block reader")
    return offset if offset <= len(data) else None


def cmd_signing_blocks(args: argparse.Namespace) -> int:
    apk = Path(args.apk)
    if not apk.is_file():
        return _fail("Signing assumption check failed", f"APK not found: {apk}")

    try:
        ids = _signing_block_ids(apk)
    except (ValueError, OSError) as exc:
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


# --------------------------------------------------------------------------------------
# selftest — the checks the checker's own defects would have passed (DB-003)
# --------------------------------------------------------------------------------------


def cmd_selftest(_args: argparse.Namespace) -> int:
    """Prove the two DB-003 defects stay fixed, using fixtures built at runtime.

    Runtime-built, never stored: a committed binary fixture is a blob nobody re-reads, and the
    repo's own rule is that fixture material is generated, not checked in.
    """
    import contextlib
    import io
    import tempfile

    failures: list[str] = []

    with tempfile.TemporaryDirectory() as tmp:
        root = Path(tmp)

        # 1. Content vs declared metadata. Two archives with identical CRC fields but different
        #    payload bytes: the CRC-metadata comparison called these equal.
        honest, tampered = root / "honest.apk", root / "tampered.apk"
        for path in (honest, tampered):
            with zipfile.ZipFile(path, "w", zipfile.ZIP_STORED) as zf:
                zf.writestr("classes.dex", "HONEST-PAYLOAD--")
        raw = tampered.read_bytes().replace(b"HONEST-PAYLOAD--", b"EVIL-PAYLOAD----")
        tampered.write_bytes(raw)  # CRC fields still describe the original bytes

        same_declared_crc = [i.CRC for i in zipfile.ZipFile(honest).infolist()] == [
            i.CRC for i in zipfile.ZipFile(tampered).infolist()
        ]
        if not same_declared_crc:
            failures.append("fixture is not exercising the defect: declared CRCs already differ")
        args = argparse.Namespace(apk_a=str(honest), apk_b=str(tampered), report=None)
        # Swallow the inner run's output: it is a NEGATIVE case, and its failure annotation would
        # otherwise read as this selftest failing.
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            tampered_verdict = cmd_compare(args)
        if tampered_verdict == 0:
            failures.append("compare() called byte-different APKs identical (CRC metadata trusted)")

        # 2. Signing-block location. The magic string inside an ENTRY must not be mistaken for the
        #    block header — the old global rfind() would have found this one.
        decoy = root / "decoy.apk"
        with zipfile.ZipFile(decoy, "w", zipfile.ZIP_STORED) as zf:
            zf.writestr("assets/payload.bin", b"x" * 32 + b"APK Sig Block 42" + b"y" * 32)
        try:
            ids = _signing_block_ids(decoy)
        except ValueError as exc:
            ids = f"raised {exc}"  # type: ignore[assignment]
        if ids is not None:
            failures.append(f"signing-block reader was fooled by entry content: {ids}")

    for failure in failures:
        print(f"  - {failure}")
    if failures:
        return _fail("Self-test failed", f"{len(failures)} regression(s) in fdroid-check itself")
    return _ok("selftest: content comparison reads bytes; signing-block reader is EOCD-anchored")


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

    p_self = sub.add_parser("selftest", help="prove this script's own DB-003 fixes still hold")
    p_self.set_defaults(func=cmd_selftest)

    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
