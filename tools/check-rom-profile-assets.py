#!/usr/bin/env python3
"""Cross-check rom-profiles.json ids against the decoded resource trees.

This is a static map check, not proof of runtime attachment. It answers one narrow question:
does a container/battery/clock id in a profile exist in the decoded APK at all?

Usage:
    python3 tools/check-rom-profile-assets.py
    python3 tools/check-rom-profile-assets.py --json
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

DECODED = {
    "oxygenos": "reverse/decoded/oneplus-phone",
    "hyperos": "reverse/decoded/xiaomi",
    "oneui": "reverse/decoded/samsung",
    "coloros": "reverse/decoded/oppo",
    "originos": "reverse/decoded/vivo",
    "magicos": "reverse/decoded/honor",
    "myos": "reverse/decoded/motorola",
    "nothingos": "reverse/decoded/nothing",
    # no decoded AOSP tree in the corpus yet
    "aosp": "",
}
FIELDS = ("containerIds", "batteryId", "clockId", "shadeContainerIds", "shadeBatteryId")


def names_in_tree(root: Path, wanted: set[str]) -> set[str]:
    found: set[str] = set()
    # Resource names show up in layouts as @id/name and in values/public.xml as name="name".
    pattern = re.compile(r'(?:@\+?id/|name=")([A-Za-z0-9_]+)"?')
    for path in list(root.glob("res/layout*/*.xml")) + list(root.glob("res/layout*/**/*.xml")) + list(root.glob("res/values*/public.xml")):
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        for m in pattern.finditer(text):
            name = m.group(1)
            if name in wanted:
                found.add(name)
    return found


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    profiles = json.load(open("app/src/main/assets/rom-profiles.json"))["profiles"]
    report = {}
    failed = False
    for profile in profiles:
        pid = profile["id"]
        decoded = DECODED.get(pid)
        if not decoded:
            report[pid] = {"error": "no decoded tree mapped"}
            if profile.get("measured"):
                failed = True
            continue
        root = Path(decoded)
        if not root.is_dir():
            report[pid] = {"error": f"missing decoded tree: {root}"}
            if profile.get("measured"):
                failed = True
            continue
        wanted = set()
        for field in FIELDS:
            value = profile.get(field)
            if isinstance(value, str):
                wanted.add(value)
            elif isinstance(value, list):
                wanted.update(value)
        found = names_in_tree(root, wanted)
        missing = sorted(wanted - found)
        report[pid] = {
            "decoded": str(root),
            "wanted": sorted(wanted),
            "found": sorted(found),
            "missing": missing,
            "measured": bool(profile.get("measured")),
        }
        if missing and profile.get("measured"):
            failed = True
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        for pid, row in report.items():
            if "error" in row:
                print(f"{pid:10} ERROR {row['error']}")
                continue
            mark = "MEASURED" if row["measured"] else "static"
            print(f"{pid:10} {mark:8} found={len(row['found'])}/{len(row['wanted'])} missing={row['missing']}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
