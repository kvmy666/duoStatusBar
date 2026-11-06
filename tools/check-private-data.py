#!/usr/bin/env python3
"""Fail when private evidence or private device data is tracked by git."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

PRIVATE_IPV4 = re.compile(
    r"\b(?:"
    r"192\.168\.\d{1,3}\.\d{1,3}"
    r"|10\.\d{1,3}\.\d{1,3}\.\d{1,3}"
    r"|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}"
    r")\b"
)
MAC = re.compile(
    r"(?<![0-9A-Fa-f:-])(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}(?![0-9A-Fa-f:-])"
)
IPV6 = re.compile(
    r"(?<![0-9A-Fa-f:])(?:[0-9A-Fa-f]{1,4}:){3,}[0-9A-Fa-f]{0,4}(?![0-9A-Fa-f:])"
    r"|\bfe80::[0-9A-Fa-f:]*"
    r"|\b(?:fc|fd)[0-9A-Fa-f]{2}:[0-9A-Fa-f:]+"
    r"|\b(?:2001|2600):[0-9A-Fa-f:]+"
)
HOME_PATH = re.compile(r"/home/abdulkarim\b")
CHECKERS = (
    ("private IPv4", PRIVATE_IPV4),
    ("MAC", MAC),
    ("IPv6", IPV6),
    ("home path", HOME_PATH),
)
DOC_SUFFIXES = (".md", ".txt")


def git(*args: str) -> str:
    return subprocess.run(
        ("git", *args),
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    ).stdout


def tracked_docs() -> list[str]:
    return [
        path
        for path in git("ls-files", "-z").split("\0")
        if path and path.lower().endswith(DOC_SUFFIXES)
    ]


def main() -> int:
    failures = 0
    evidence = git("ls-files", "--", "docs/evidence").splitlines()
    if evidence:
        failures += 1
        print("error: docs/evidence must not be tracked; move it outside the repository:", file=sys.stderr)
        for path in evidence:
            print(f"  {path}", file=sys.stderr)

    for path in tracked_docs():
        text = Path(path).read_text(encoding="utf-8", errors="replace")
        for line_no, line in enumerate(text.splitlines(), start=1):
            for label, pattern in CHECKERS:
                for match in pattern.finditer(line):
                    failures += 1
                    print(
                        f"error: {path}:{line_no}: tracked {label} value: {match.group(0)}",
                        file=sys.stderr,
                    )

    if failures:
        print("private-data check failed", file=sys.stderr)
        return 1

    print(f"private-data check passed: 0 tracked evidence files, {len(tracked_docs())} tracked docs clean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
