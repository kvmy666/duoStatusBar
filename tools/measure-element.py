#!/usr/bin/env python3
"""Measure one Duo element in a reference image, in units of the ring's outer diameter.

Reports the ring bbox, the digit cap height and the Wi-Fi/sphere extents so the design can be scaled
from the reference rather than by eye.

    python tools/measure-element.py docs/reference/duo-reference-02-percentage-50-16.jpg 0
    python tools/measure-element.py docs/reference/duo-reference-02-percentage-50-16.jpg 1
"""
from __future__ import annotations

import sys

import numpy as np
from PIL import Image


def dark_bbox(mask: np.ndarray) -> tuple[int, int, int, int] | None:
    ys, xs = np.nonzero(mask)
    if len(xs) == 0:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())


def main() -> int:
    path, which = sys.argv[1], int(sys.argv[2])
    im = Image.open(path).convert("L")
    a = np.asarray(im).astype(float)
    mask = a < 110
    h, w = mask.shape
    # Two elements side by side in image 2: split down the middle.
    x0, x1 = (0, w // 2) if which == 0 else (w // 2, w)
    m = np.zeros_like(mask)
    m[:, x0:x1] = mask[:, x0:x1]

    # The ring is the widest dark run; find the row with the widest span, then the full ring bbox
    # by keeping only the connected blob around it (approximated by the rows it spans).
    full = dark_bbox(m)
    print(f"{path} element {which}: dark bbox {full}")

    # Digit band = the top 25% of the element; ring = the rest.
    fx0, fy0, fx1, fy1 = full
    band = fy0 + int((fy1 - fy0) * 0.28)
    top = m[fy0:band, x0:x1]
    tb = dark_bbox(top)
    if tb:
        print(f"  digits bbox: w {tb[2] - tb[0] + 1}, h {tb[3] - tb[1] + 1}")

    # Ring: widest horizontal extent in the lower 80%.
    lower = m[band:fy1, x0:x1]
    widths = lower.sum(axis=1)
    if widths.size:
        widest = int(widths.max())
        row = int(np.argmax(widths)) + band
        print(f"  ring widest span (px): {widest} at row {row}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
