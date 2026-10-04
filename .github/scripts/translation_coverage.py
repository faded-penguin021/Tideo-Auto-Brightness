#!/usr/bin/env python3
"""Per-language translation coverage as shields.io endpoint JSON (DD-028).

Usage: translation_coverage.py <res-dir> <out-dir>

For every values-*/strings.xml under <res-dir>, writes <out-dir>/<language-tag>.json and prints a
Markdown table for the job summary. Coverage is the share of translatable default strings
(string, plurals, string-array) that the locale file also defines, rounded down to a whole percent.
"""
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RESOURCE_TAGS = {"string", "plurals", "string-array"}
MISSING_SHOWN = 50


def keys(path, translatable_only):
    return {
        e.get("name")
        for e in ET.parse(path).getroot()
        if e.tag in RESOURCE_TAGS and not (translatable_only and e.get("translatable") == "false")
    }


def language_tag(qualifier):
    if qualifier.startswith("b+"):
        return qualifier[2:].replace("+", "-")
    return qualifier.replace("-r", "-")


def color(percent):
    if percent == 100:
        return "brightgreen"
    if percent >= 90:
        return "green"
    if percent >= 75:
        return "yellowgreen"
    if percent >= 50:
        return "yellow"
    return "orange"


def main(res_dir, out_dir):
    res, out = Path(res_dir), Path(out_dir)
    source = keys(res / "values" / "strings.xml", translatable_only=True)
    locales = sorted(d for d in res.glob("values-*") if (d / "strings.xml").is_file())
    if not locales:
        sys.exit(f"no values-*/strings.xml under {res}")
    out.mkdir(parents=True, exist_ok=True)

    print("| Language | Translated | Coverage | Missing |")
    print("|---|---|---|---|")
    for d in locales:
        tag = language_tag(d.name[len("values-"):])
        have = keys(d / "strings.xml", translatable_only=False) & source
        percent = len(have) * 100 // len(source)
        badge = {"schemaVersion": 1, "label": tag, "message": f"{percent}% translated", "color": color(percent)}
        (out / f"{tag}.json").write_text(json.dumps(badge) + "\n", encoding="utf-8")
        missing = sorted(source - have)
        shown = ", ".join(f"`{k}`" for k in missing[:MISSING_SHOWN]) or "none"
        if len(missing) > MISSING_SHOWN:
            shown += f" and {len(missing) - MISSING_SHOWN} more"
        print(f"| {tag} | {len(have)} of {len(source)} | {percent}% | {shown} |")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
