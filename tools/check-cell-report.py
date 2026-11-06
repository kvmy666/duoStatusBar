#!/usr/bin/env python3
"""Check a Duo report's cellular evidence against the four-dot values.

Usage:
    tools/check-cell-report.py path/to/diagnostics.txt
    tools/check-cell-report.py path/to/report.zip
    tools/check-cell-report.py --self-test

The legacy line is emitted by CellReader.diagnostic:
    cell raw level=4 levels=[4, 4] generation=5G max=5 subs=[2, 1] airplane=false sim=auto

It is kept for compatibility, but its level/levels fields are *already mapped dots*, not raw values.
A report that contains only this line is rejected as legacy-only.

The raw evidence is emitted by the 1.7 module:
    cell sample sub=2 slot=0 raw=4 count=5 maxIndex=4 rat=Lte dbm=-97 metric=rsrp dots=4 src=per-sub airplane=false
    cell pick sim=auto slot=-1 sub=2 dots=4 src=default fallback=false defaultSub=2

This is a verification helper only; it does not read or change the device.
"""
from __future__ import annotations

import contextlib
import io
import re
import sys
import zipfile
from pathlib import Path

RAW = re.compile(
    r"cell raw level=(-?\d+) levels=\[([^\]]*)\] generation=(\S+) "
    r"max=(-?\d+) subs=\[([^\]]*)\] airplane=(true|false) sim=(\S+)"
)
SAMPLE = re.compile(
    r"cell sample sub=(-?\d+) slot=(-?\d+) raw=(-?\d+) count=(-?\d+) "
    r"maxIndex=(-?\d+) rat=(\S+) dbm=(-?\d+) metric=(\S+) dots=(-?\d+) "
    r"src=(\S+) airplane=(true|false)"
)
PICK = re.compile(
    r"cell pick sim=(\S+) slot=(-?\d+) sub=(-?\d+) dots=(-?\d+) "
    r"src=(\S+) fallback=(true|false) defaultSub=(-?\d+)"
)
SNAPSHOT = re.compile(
    r"cellLevel=(-?\d+) generation=(\S+) mobileData=(true|false) cellLevels=\[([^\]]*)\]"
)

SAMPLE_SOURCES = {"per-sub", "unavailable"}
PICK_SOURCES = {"per-sub", "default", "stale", "fallback"}


def max_index(raw: int, count: int) -> int:
    """The highest legal raw index, using the same rule as SignalMapping.cellMaxIndex."""
    count = max(1, count)
    return count if raw >= count else max(1, count - 1)


def dots_indexed(raw: int, top: int) -> int:
    """Round-to-nearest, using the same integer arithmetic as SignalMapping.cellDotsIndexed."""
    if raw <= 0:
        return 0
    top = max(1, top)
    return max(1, min(4, (4 * raw + top // 2) // top))


def parse_ints(value: str) -> list[int]:
    return [int(x.strip()) for x in value.split(",") if x.strip()]


def read_text(path: Path) -> str:
    if path.suffix == ".zip":
        with zipfile.ZipFile(path) as z:
            names = z.namelist()
            for name in ("diagnostics.txt", "module-log.txt"):
                if name in names:
                    return z.read(name).decode("utf-8", "replace")
        raise SystemExit(f"{path}: no diagnostics.txt/module-log.txt")
    return path.read_text(encoding="utf-8", errors="replace")


def check(text: str) -> int:
    raw_matches = list(RAW.finditer(text))
    samples = list(SAMPLE.finditer(text))
    picks = list(PICK.finditer(text))
    snapshots = list(SNAPSHOT.finditer(text))
    failures = 0

    if not samples:
        if raw_matches:
            print(
                "FAIL: legacy-only report: 'cell raw level=' prints already-mapped dots, "
                "not raw SignalStrength values; the 1.7 'cell sample' line is required",
                file=sys.stderr,
            )
        else:
            print(
                "FAIL: no 'cell sample' line; the 1.7 module (or a legacy 'cell raw' line) is required",
                file=sys.stderr,
            )
        return 1

    print(
        f"legacy lines: {len(raw_matches)}; samples: {len(samples)}; "
        f"picks: {len(picks)}; snapshots: {len(snapshots)}"
    )

    # The legacy line cannot prove raw, but its mapped fields still have to be well formed.
    for i, m in enumerate(raw_matches):
        level, levels, gen, stock, subs, airplane, sim = m.groups()
        level_i = int(level)
        stock_i = int(stock)
        levels_i = parse_ints(levels)
        print(
            f"  legacy [{i}] dots={level_i} mappedLevels={levels_i} max={stock_i} "
            f"gen={gen} subs={subs} airplane={airplane} sim={sim}"
        )
        if stock_i < 1 or stock_i > 8:
            print(f"    FAIL: implausible stock level count {stock_i}")
            failures += 1
        if level_i < 0 or level_i > 4:
            print(f"    FAIL: legacy level {level_i} outside 0..4")
            failures += 1
        if any(x < 0 or x > 4 for x in levels_i):
            print(f"    FAIL: legacy levels {levels_i} outside 0..4")
            failures += 1

    known: set[tuple[int, int]] = set()
    for i, m in enumerate(samples):
        sub, slot, raw, count, top, rat, dbm, metric, dots, src, airplane = m.groups()
        sub_i, slot_i, raw_i, count_i = int(sub), int(slot), int(raw), int(count)
        top_i, dbm_i, dots_i = int(top), int(dbm), int(dots)
        known.add((slot_i, sub_i))
        print(
            f"  sample [{i}] sub={sub_i} slot={slot_i} raw={raw_i} count={count_i} "
            f"maxIndex={top_i} rat={rat} dbm={dbm_i} {metric} dots={dots_i} "
            f"src={src} airplane={airplane}"
        )
        if count_i < 1 or count_i > 8:
            print(f"    FAIL: implausible stock level count {count_i}")
            failures += 1
        if top_i < 1 or top_i > 8:
            print(f"    FAIL: implausible maxIndex {top_i}")
            failures += 1
        if dots_i < 0 or dots_i > 4:
            print(f"    FAIL: dots {dots_i} outside 0..4")
            failures += 1
        if src not in SAMPLE_SOURCES:
            print(f"    FAIL: unknown source {src!r} (expected {sorted(SAMPLE_SOURCES)})")
            failures += 1
        if raw_i < 0:
            if src != "unavailable":
                print(f"    FAIL: raw {raw_i} is marked src={src}; a failed read must be unavailable")
                failures += 1
            expected_top = max_index(raw_i, count_i)
        else:
            if src == "unavailable":
                print(f"    FAIL: raw {raw_i} is marked unavailable")
                failures += 1
            expected_top = max_index(raw_i, count_i)
            if raw_i > top_i:
                print(f"    FAIL: raw {raw_i} is above maxIndex {top_i}")
                failures += 1
            expected_dots = dots_indexed(raw_i, top_i)
            if dots_i != expected_dots:
                print(f"    FAIL: raw={raw_i} maxIndex={top_i} -> dots={expected_dots}, logged {dots_i}")
                failures += 1
        if top_i != expected_top:
            print(f"    FAIL: maxIndex {top_i} should be {expected_top} for count={count_i}")
            failures += 1

    if not picks:
        print("FAIL: no 'cell pick' line; the selected subscription/source is unproven", file=sys.stderr)
        failures += 1
    for i, m in enumerate(picks):
        sim, slot, sub, dots, src, fallback, default_sub = m.groups()
        slot_i, sub_i, dots_i, default_sub_i = int(slot), int(sub), int(dots), int(default_sub)
        print(
            f"  pick [{i}] sim={sim} slot={slot_i} sub={sub_i} dots={dots_i} "
            f"src={src} fallback={fallback} defaultSub={default_sub_i}"
        )
        if dots_i < 0 or dots_i > 4:
            print(f"    FAIL: picked dots {dots_i} outside 0..4")
            failures += 1
        if src not in PICK_SOURCES:
            print(f"    FAIL: unknown pick source {src!r} (expected {sorted(PICK_SOURCES)})")
            failures += 1
        if (src == "fallback") != (fallback == "true"):
            print(f"    FAIL: src={src} and fallback={fallback} disagree")
            failures += 1
        if src == "per-sub" and (slot_i, sub_i) not in known:
            print(f"    FAIL: pick names slot={slot_i} sub={sub_i}, which has no sample")
            failures += 1

    if snapshots and picks:
        observed = int(snapshots[-1].group(1))
        expected = int(picks[-1].group(4))
        if observed != expected:
            print(f"    FAIL: snapshot cellLevel={observed}, pick dots={expected}")
            failures += 1

    if snapshots:
        last = snapshots[-1]
        if last.group(3) != "true":
            print("    note: mobile data is off in the final snapshot")
        print(
            f"snapshot: cellLevel={last.group(1)} generation={last.group(2)} "
            f"cellLevels={last.group(4)}"
        )
    return 1 if failures else 0


def self_test() -> int:
    full4 = (
        "cell sample sub=1 slot=0 raw=3 count=4 maxIndex=3 rat=Lte dbm=-97 metric=rsrp "
        "dots=4 src=per-sub airplane=false\n"
        "cell pick sim=sim1 slot=0 sub=1 dots=4 src=per-sub fallback=false defaultSub=1\n"
    )
    full5 = (
        "cell sample sub=2 slot=0 raw=4 count=5 maxIndex=4 rat=Lte dbm=-97 metric=rsrp "
        "dots=4 src=per-sub airplane=false\n"
        "cell pick sim=sim2 slot=0 sub=2 dots=4 src=per-sub fallback=false defaultSub=2\n"
    )
    count6 = (
        "cell sample sub=2 slot=0 raw=4 count=6 maxIndex=5 rat=Lte dbm=-97 metric=rsrp "
        "dots=3 src=per-sub airplane=false\n"
        "cell pick sim=sim2 slot=0 sub=2 dots=3 src=per-sub fallback=false defaultSub=2\n"
    )
    bad_dots = (
        "cell sample sub=2 slot=0 raw=4 count=5 maxIndex=4 rat=Lte dbm=-97 metric=rsrp "
        "dots=1 src=per-sub airplane=false\n"
        "cell pick sim=sim2 slot=0 sub=2 dots=1 src=per-sub fallback=false defaultSub=2\n"
    )
    bad_top = (
        "cell sample sub=2 slot=0 raw=3 count=4 maxIndex=4 rat=Lte dbm=-97 metric=rsrp "
        "dots=4 src=per-sub airplane=false\n"
        "cell pick sim=sim2 slot=0 sub=2 dots=4 src=per-sub fallback=false defaultSub=2\n"
    )
    legacy_only = (
        "cell raw level=4 levels=[4, 4] generation=5G max=5 subs=[2, 1] "
        "airplane=false sim=auto\n"
    )
    cases = [
        ("4-level full maps to four", full4, True),
        ("5-level identity maps to four", full5, True),
        ("count 6 / maxIndex 5 maps raw 4 to three", count6, True),
        ("raw 4 must not map to one", bad_dots, False),
        ("maxIndex must match count/raw", bad_top, False),
        ("legacy-only is rejected", legacy_only, False),
    ]
    failures = 0
    for name, text, should_pass in cases:
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
            code = check(text)
        ok = (code == 0) == should_pass
        print(f"{'PASS' if ok else 'FAIL'} self-test: {name}")
        if not ok:
            print(buf.getvalue())
            failures += 1
    return 1 if failures else 0


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    if sys.argv[1] == "--self-test":
        return self_test()
    return check(read_text(Path(sys.argv[1])))


if __name__ == "__main__":
    raise SystemExit(main())
