"""Trace a bitmap icon into Rive PointsPath vertices.

The status-bar glyphs that are not arcs (the Wi-Fi dot, the DND crescent, the
airplane) are traced from the reference art rather than re-derived: a path drawn
by hand never quite matches, and a triangle-plus-corner-radius never matched the
Wi-Fi dot at all.

Pipeline: mask -> optionally fill holes -> 0.5 contour -> Douglas-Peucker
simplify (keeps the corners sharp and the arcs cheap) -> scale and centre into
design units -> StraightVertex lines.

`--fill-holes` is what turns an *outlined* icon (a crescent drawn as a stroked
outline, whose interior is transparent) into the solid shape it encloses.

Usage:
    python tools/trace-shape-to-rive.py IMAGE [options]

Options:
    --crop x0,y0,x1,y1   region to read (default: whole image)
    --mask alpha|dark    alpha > 128, or luminance < 128 (default: dark)
    --fill-holes         fill enclosed transparent regions (outlined icons)
    --width N            scale so the shape is N design units wide (default 40)
    --center X,Y         where the shape's centre sits, in design units
    --rotate DEG         rotate before scaling (e.g. 90 to turn a plane upright)
    --tolerance F        simplification tolerance as a fraction of the size (default 0.004)
"""

import argparse
import math
import sys

import numpy as np
from PIL import Image


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("image")
    p.add_argument("--crop", default=None)
    p.add_argument("--mask", choices=["alpha", "dark"], default="dark")
    p.add_argument("--fill-holes", action="store_true")
    p.add_argument("--width", type=float, default=40.0)
    p.add_argument("--center", default="0,0")
    p.add_argument("--rotate", type=float, default=0.0)
    p.add_argument("--tolerance", type=float, default=0.004)
    return p.parse_args()


def mask_of(img: Image.Image, kind: str) -> np.ndarray:
    if kind == "alpha":
        return np.array(img.convert("RGBA"))[:, :, 3] > 128
    return np.array(img.convert("L")).astype(float) < 128


def fill_holes(mask: np.ndarray) -> np.ndarray:
    """Flood the background inwards from the border; anything unreached is enclosed."""
    from scipy import ndimage

    return ndimage.binary_fill_holes(mask)


def trace(mask: np.ndarray) -> np.ndarray:
    """The 0.5 contour of the mask, as (x, y) pixel pairs."""
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    padded = np.pad(mask.astype(float), 1)
    contours = plt.contour(padded, levels=[0.5])
    outline = max(contours.allsegs[0], key=len)
    return outline - 1.0  # undo the pad


def simplify(points: np.ndarray, tolerance: float) -> np.ndarray:
    """Douglas-Peucker: keep the corners, drop the redundant points along smooth runs."""
    keep = np.zeros(len(points), bool)
    keep[0] = keep[-1] = True

    def split(lo: int, hi: int) -> None:
        if hi <= lo + 1:
            return
        a, b = points[lo], points[hi]
        seg = b - a
        length = np.hypot(*seg)
        if length == 0:
            dist = np.hypot(*(points[lo + 1 : hi] - a).T)
        else:
            dist = np.abs(np.cross(seg, points[lo + 1 : hi] - a)) / length
        far = int(np.argmax(dist))
        if dist[far] > tolerance:
            idx = lo + 1 + far
            keep[idx] = True
            split(lo, idx)
            split(idx, hi)

    split(0, len(points) - 1)
    return points[keep]


def main() -> None:
    args = parse_args()
    img = Image.open(args.image)
    if args.crop:
        img = img.crop(tuple(int(v) for v in args.crop.split(",")))

    mask = mask_of(img, args.mask)
    if args.fill_holes:
        mask = fill_holes(mask)

    pts = trace(mask)
    size = max(pts[:, 0].max() - pts[:, 0].min(), pts[:, 1].max() - pts[:, 1].min())
    pts = simplify(pts, args.tolerance * size)

    # Centre on the shape's own bbox, optionally rotate, then scale to --width.
    pts = pts - (pts.max(axis=0) + pts.min(axis=0)) / 2
    if args.rotate:
        t = math.radians(args.rotate)
        rot = np.array([[math.cos(t), -math.sin(t)], [math.sin(t), math.cos(t)]])
        pts = pts @ rot.T
    span = max(pts[:, 0].max() - pts[:, 0].min(), pts[:, 1].max() - pts[:, 1].min())
    pts *= args.width / span

    cx, cy = (float(v) for v in args.center.split(","))
    pts += (cx, cy)

    for x, y in pts:
        print(f'                    <StraightVertex x="{x:.2f}" y="{y:.2f}" />')

    w = pts[:, 0].max() - pts[:, 0].min()
    h = pts[:, 1].max() - pts[:, 1].min()
    print(
        f"# {len(pts)} points, {w:.2f} x {h:.2f} units, centre ({cx:.2f}, {cy:.2f})",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
