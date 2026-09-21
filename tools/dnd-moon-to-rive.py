#!/usr/bin/env python3
"""Convert the device's real DND status icon into Rive PointsPath vertices.

The moon is **extracted from the DND icon itself**, not rebuilt: this is the `pathData` of
`drawable/stat_sys_dnd` in the target device's SystemUI APK, read with

    aapt2 dump xmltree --file res/drawable/stat_sys_dnd.xml reverse/SystemUI-device.apk

and pasted verbatim below. Rebuilding the crescent by hand would ship a second, unverified
geometry - exactly what this project's evidence rule forbids.

Rive authors cubic curves as vertices with polar handles (`inRotation`/`inDistance`,
`outRotation`/`outDistance`), and **rotation is radians** (full turn = 6.2831855 - confirmed with
`rive docs format`, not assumed). This script parses the SVG path, converts every cubic segment's
absolute control points into those handles, bakes a uniform scale and centring into the vertex
coordinates, and prints the RML fragment for `scene.rml`.

Run:
    python tools/dnd-moon-to-rive.py            # print the vertices
    python tools/dnd-moon-to-rive.py --scale 1.9 --check   # validate handles by resampling
"""

from __future__ import annotations

import argparse
import math
import re
from dataclasses import dataclass

# ---------------------------------------------------------------------------------------------
# Provenance: android:pathData of res/drawable/stat_sys_dnd.xml, SystemUI-device.apk (OOS 16).
# viewportWidth=18, viewportHeight=16, fillType=1 (evenOdd).
# ---------------------------------------------------------------------------------------------
DND_MOON_PATH = (
    "M8.34 2.6c0.1-0.15 0.11-0.34 0.01-0.5-0.1-0.15-0.28-0.23-0.46-0.2"
    "C5 2.42 2.8 4.95 2.8 8c0 3.42 2.78 6.2 6.2 6.2 3.05 0 5.58-2.2 6.1-5.1"
    "0.03-0.17-0.05-0.36-0.2-0.45-0.16-0.1-0.35-0.1-0.5 0.01"
    "-0.71 0.51-1.58 0.8-2.53 0.8-2.4 0-4.34-1.94-4.34-4.34 0-0.94 0.3-1.8 0.8-2.52Z"
)
VIEWPORT_W, VIEWPORT_H = 18.0, 16.0

NUMBER = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")


@dataclass
class Cubic:
    """One absolute cubic segment: start, two controls, end."""
    p0: tuple[float, float]
    c1: tuple[float, float]
    c2: tuple[float, float]
    p1: tuple[float, float]


def parse_path(d: str) -> list[Cubic]:
    """Parse the SVG path into absolute cubic segments (the icon has no other curve types)."""
    tokens = re.findall(r"[MmLlHhVvCcSsZz]|" + NUMBER.pattern, d)
    i = 0
    cur = (0.0, 0.0)
    start = (0.0, 0.0)
    prev_c2: tuple[float, float] | None = None
    cmd = ""
    out: list[Cubic] = []

    def num() -> float:
        nonlocal i
        v = float(tokens[i])
        i += 1
        return v

    def point(rel: bool) -> tuple[float, float]:
        x, y = num(), num()
        return (cur[0] + x, cur[1] + y) if rel else (x, y)

    while i < len(tokens):
        t = tokens[i]
        if re.match(r"[A-Za-z]", t):
            cmd = t
            i += 1
            if cmd in "Zz":
                # Straight close back to the subpath start.
                if cur != start:
                    out.append(Cubic(cur, cur, start, start))
                cur = start
                prev_c2 = None
                continue
        if cmd in "Mm":
            cur = point(cmd == "m")
            start = cur
            prev_c2 = None
            cmd = "l" if cmd == "m" else "L"
        elif cmd in "Ll":
            p = point(cmd == "l")
            out.append(Cubic(cur, cur, p, p))
            cur = p
            prev_c2 = None
        elif cmd in "Hh":
            x = num()
            p = (cur[0] + x, cur[1]) if cmd == "h" else (x, cur[1])
            out.append(Cubic(cur, cur, p, p))
            cur = p
            prev_c2 = None
        elif cmd in "Vv":
            y = num()
            p = (cur[0], cur[1] + y) if cmd == "v" else (cur[0], y)
            out.append(Cubic(cur, cur, p, p))
            cur = p
            prev_c2 = None
        elif cmd in "Cc":
            rel = cmd == "c"
            c1 = point(rel)
            c2 = point(rel)
            p1 = point(rel)
            out.append(Cubic(cur, c1, c2, p1))
            cur = p1
            prev_c2 = c2
        elif cmd in "Ss":
            rel = cmd == "s"
            # First control mirrors the previous one about the current point.
            c1 = (2 * cur[0] - prev_c2[0], 2 * cur[1] - prev_c2[1]) if prev_c2 else cur
            c2 = point(rel)
            p1 = point(rel)
            out.append(Cubic(cur, c1, c2, p1))
            cur = p1
            prev_c2 = c2
        else:
            raise ValueError(f"unsupported path command {cmd!r} at token {i}")
    return out


def bounds(segments: list[Cubic]) -> tuple[float, float, float, float]:
    """Sampled bounding box of the cubic segments (fine enough for centring an icon)."""
    xs, ys = [], []
    for s in segments:
        for k in range(33):
            t = k / 32
            u = 1 - t
            x = u**3 * s.p0[0] + 3 * u * u * t * s.c1[0] + 3 * u * t * t * s.c2[0] + t**3 * s.p1[0]
            y = u**3 * s.p0[1] + 3 * u * u * t * s.c1[1] + 3 * u * t * t * s.c2[1] + t**3 * s.p1[1]
            xs.append(x)
            ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


def _handle(origin: tuple[float, float], control: tuple[float, float]) -> tuple[float, float]:
    dx, dy = control[0] - origin[0], control[1] - origin[1]
    return math.atan2(dy, dx), math.hypot(dx, dy)


def vertices(segments: list[Cubic], scale: float, cx: float, cy: float) -> list[dict]:
    """Absolute cubics -> per-point polar handles, scaled and centred on (cx, cy).

    The handles are measured in the *scaled* space on purpose: rotation is scale-invariant but the
    distance is not, and mixing an SVG-unit distance with a scaled position is what makes a
    reconstructed curve disagree with the source.
    """
    pts: list[dict] = []

    def to_view(p: tuple[float, float]) -> tuple[float, float]:
        return ((p[0] - cx) * scale, (p[1] - cy) * scale)

    def add(p: tuple[float, float]) -> dict:
        x, y = to_view(p)
        v = {"x": x, "y": y,
             "inRotation": 0.0, "inDistance": 0.0, "outRotation": 0.0, "outDistance": 0.0}
        pts.append(v)
        return v

    add(segments[0].p0)
    for seg in segments:
        # The segment leaving the previous point sets that point's out handle.
        out_rot, out_dist = _handle(to_view(seg.p0), to_view(seg.c1))
        pts[-1]["outRotation"], pts[-1]["outDistance"] = out_rot, out_dist
        # The segment entering the next point sets its in handle.
        in_rot, in_dist = _handle(to_view(seg.p1), to_view(seg.c2))
        v = add(seg.p1)
        v["inRotation"], v["inDistance"] = in_rot, in_dist

    # A closed path's last segment ends where the first begins. Rive wants that point once, with the
    # closing curve's handle living on the first vertex - so the duplicate is folded back in rather
    # than emitted (a second coincident vertex would double the start of the fill).
    if len(pts) > 1:
        last = pts[-1]
        if math.hypot(last["x"] - pts[0]["x"], last["y"] - pts[0]["y"]) < 1e-6:
            pts[0]["inRotation"], pts[0]["inDistance"] = last["inRotation"], last["inDistance"]
            pts.pop()
        else:
            # A genuine straight closure: no handles, and the final point runs back to the start.
            last["outRotation"], last["outDistance"] = 0.0, 0.0
            pts[0]["inRotation"], pts[0]["inDistance"] = 0.0, 0.0
    return pts


def resample_error(segments: list[Cubic], verts: list[dict], scale: float, cx: float, cy: float) -> float:
    """Max distance between the authored cubics and the same cubics rebuilt from the handles."""
    def back(v: dict, which: str) -> tuple[float, float]:
        rot, dist = v[f"{which}Rotation"], v[f"{which}Distance"]
        return (v["x"] + dist * math.cos(rot), v["y"] + dist * math.sin(rot))

    worst = 0.0
    for i, seg in enumerate(segments):
        p0 = verts[i]
        p1 = verts[(i + 1) % len(verts)]
        c1 = back(p0, "out")
        c2 = back(p1, "in")
        for k in range(33):
            t = k / 32
            u = 1 - t
            sx = u**3 * seg.p0[0] + 3 * u * u * t * seg.c1[0] + 3 * u * t * t * seg.c2[0] + t**3 * seg.p1[0]
            sy = u**3 * seg.p0[1] + 3 * u * u * t * seg.c1[1] + 3 * u * t * t * seg.c2[1] + t**3 * seg.p1[1]
            rx = u**3 * p0["x"] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t**3 * p1["x"]
            ry = u**3 * p0["y"] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t**3 * p1["y"]
            # Undo the scale/centre to compare in the original SVG space.
            ox, oy = rx / scale + cx, ry / scale + cy
            worst = max(worst, math.hypot(ox - sx, oy - sy))
    return worst


def android_path(segments: list[Cubic], scale: float, cx: float, cy: float) -> str:
    """The same geometry as Android `Path` calls, for the no-native Canvas fallback."""
    def v(p: tuple[float, float]) -> tuple[float, float]:
        return ((p[0] - cx) * scale, (p[1] - cy) * scale)

    lines = [f"        path.moveTo({v(segments[0].p0)[0]:.3f}f, {v(segments[0].p0)[1]:.3f}f)"]
    for seg in segments:
        c1, c2, p1 = v(seg.c1), v(seg.c2), v(seg.p1)
        lines.append(f"        path.cubicTo({c1[0]:.3f}f, {c1[1]:.3f}f, "
                     f"{c2[0]:.3f}f, {c2[1]:.3f}f, {p1[0]:.3f}f, {p1[1]:.3f}f)")
    lines.append("        path.close()")
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--scale", type=float, default=1.9, help="uniform scale (view units per SVG unit)")
    ap.add_argument("--check", action="store_true", help="assert the handles reproduce the cubics")
    ap.add_argument("--android", action="store_true", help="emit Kotlin Path calls instead of RML")
    args = ap.parse_args()

    segments = parse_path(DND_MOON_PATH)
    x0, y0, x1, y1 = bounds(segments)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2

    if args.android:
        print(f"// Extracted from stat_sys_dnd (viewport {VIEWPORT_W:g}x{VIEWPORT_H:g}), "
              f"scale {args.scale}, centred at ({cx:.2f},{cy:.2f}). Generated by "
              f"tools/dnd-moon-to-rive.py --android. Do not hand-edit.")
        print(android_path(segments, args.scale, cx, cy))
        return 0

    verts = vertices(segments, args.scale, cx, cy)

    if args.check:
        err = resample_error(segments, verts, args.scale, cx, cy)
        print(f"segments={len(segments)} vertices={len(verts)} bbox={x1 - x0:.2f}x{y1 - y0:.2f} "
              f"centre=({cx:.3f},{cy:.3f}) max_resample_error={err:.2e} view_units")
        if err > 1e-9:
            raise SystemExit("handles do not reproduce the authored cubics")
        return 0

    print(f"<!-- Extracted from stat_sys_dnd (viewport {VIEWPORT_W:g}x{VIEWPORT_H:g}), "
          f"scale {args.scale}, centred at ({cx:.2f},{cy:.2f}). Do not hand-edit. -->")
    print('<PointsPath isClosed="true" name="Path">')
    for v in verts:
        print(f'    <CubicDetachedVertex x="{v["x"]:.3f}" y="{v["y"]:.3f}" '
              f'inRotation="{v["inRotation"]:.6f}" inDistance="{v["inDistance"]:.3f}" '
              f'outRotation="{v["outRotation"]:.6f}" outDistance="{v["outDistance"]:.3f}" />')
    print("</PointsPath>")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
