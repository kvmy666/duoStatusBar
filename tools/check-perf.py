#!/usr/bin/env python3
"""Check a `dumpsys gfxinfo <pkg> framestats` capture against the Phase 7 budgets.

Usage:
    adb shell dumpsys gfxinfo com.android.systemui framestats > /tmp/gfx.txt
    python3 tools/check-perf.py /tmp/gfx.txt
    python3 tools/check-perf.py /tmp/gfx.txt --p95 16.7 --max 50

Columns are resolved from the header, because API 36 added FrameTimelineVsyncId and shifted the
indices. A missing header or no complete frame is an error, not a pass; an empty capture is never
evidence. When the app is idle, the row set can be tiny; take the capture after a deliberate scroll.
"""
from __future__ import annotations

import argparse
import sys


def frame_times(text: str) -> list[float]:
    header: list[str] | None = None
    intended_at = completed_at = -1
    out: list[float] = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        if line.startswith("Flags,"):
            header = [p.strip() for p in line.split(",")]
            intended_at = header.index("IntendedVsync") if "IntendedVsync" in header else -1
            completed_at = header.index("FrameCompleted") if "FrameCompleted" in header else -1
            continue
        if header is None or intended_at < 0 or completed_at < 0:
            continue
        parts = [p.strip() for p in line.split(",")]
        if len(parts) <= max(intended_at, completed_at):
            continue
        try:
            intended = int(parts[intended_at])
            completed = int(parts[completed_at])
        except ValueError:
            continue
        if completed <= 0 or intended <= 0 or completed < intended:
            continue
        out.append((completed - intended) / 1_000_000.0)
    return out


def percentile(values: list[float], pct: float) -> float:
    ordered = sorted(values)
    if not ordered:
        return 0.0
    index = max(0, min(len(ordered) - 1, int(round((pct / 100.0) * (len(ordered) - 1)))))
    return ordered[index]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path")
    parser.add_argument("--p95", type=float, default=16.7)
    parser.add_argument("--max", type=float, default=50.0)
    args = parser.parse_args()
    try:
        text = open(args.path, encoding="utf-8", errors="replace").read()
    except OSError as exc:
        print(f"ERROR: {exc}")
        return 2
    times = frame_times(text)
    if not times:
        print("ERROR: no complete frame rows; capture is not evidence")
        return 2
    p95 = percentile(times, 95)
    worst = max(times)
    print(f"frames={len(times)} p95={p95:.2f}ms max={worst:.2f}ms")
    if p95 > args.p95 or worst > args.max:
        print(f"FAIL: budget p95<={args.p95}ms max<={args.max}ms")
        return 1
    print("PASS: Phase 7 frame budget")
    return 0


if __name__ == "__main__":
    sys.exit(main())
