#!/usr/bin/env python3
"""Measure the Duo reference screenshots so the design is read, not guessed.

Everything is reported relative to the ring's outer diameter, so the numbers transfer to the 120-unit
design box regardless of the screenshot's scale.

    python tools/measure-reference.py docs/reference/duo-reference-01-percentage-off.jpg
    python tools/measure-reference.py docs/reference/duo-reference-02-percentage-50-16.jpg
"""
from __future__ import annotations

import sys

import numpy as np
from PIL import Image


def load_dark(path: str) -> np.ndarray:
    im = Image.open(path).convert("L")
    a = np.asarray(im).astype(float)
    # The references are dark artwork on a light panel; keep only the ink.
    return a < 100


def bbox(mask: np.ndarray) -> tuple[int, int, int, int]:
    ys, xs = np.nonzero(mask)
    return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())


def report(name: str, mask: np.ndarray, crop: tuple[int, int, int, int] | None = None) -> None:
    m = mask
    if crop:
        x0, y0, x1, y1 = crop
        m = np.zeros_like(mask)
        m[y0:y1, x0:x1] = mask[y0:y1, x0:x1]
    x0, y0, x1, y1 = bbox(m)
    print(f"\n== {name} ==")
    print(f"dark bbox: x {x0}..{x1} ({x1 - x0 + 1}), y {y0}..{y1} ({y1 - y0 + 1})")


def main() -> int:
    for path in sys.argv[1:]:
        mask = load_dark(path)
        h, w = mask.shape
        print(f"\n### {path}  {w}x{h}")
        report("whole image", mask)
        # Top band is often the status bar; the element sits in the middle/lower area.
        report("middle band", mask, (0, int(h * 0.15), w, int(h * 0.85)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
