"""Extract the Wi-Fi dot outline from an Apple reference render.

The dot is the one shape in the Wi-Fi glyph that is not a clean arc, so it is
traced from a reference image rather than re-derived: contour at 0.5, resampled
evenly by arc length, converted into design units relative to the Wi-Fi centre
(so the numbers drop straight into `rive/duo/scene.rml`).

Usage:
    python tools/extract-wifi-dot.py <reference-image> <cx> <cy> <px-per-unit> <crop>
"""

import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

POINTS = 32


def main() -> None:
    path = sys.argv[1]
    cx, cy, scale = float(sys.argv[2]), float(sys.argv[3]), float(sys.argv[4])
    x0, y0, x1, y1 = (int(v) for v in sys.argv[5].split(","))

    image = np.array(Image.open(path).convert("L")).astype(float)
    sub = image[y0:y1, x0:x1]
    contours = plt.contour(sub, levels=[127.5])
    outline = max(contours.allsegs[0], key=len)

    # Resample evenly by arc length so the vertices are not bunched at the corners.
    step = np.r_[0, np.cumsum(np.hypot(np.diff(outline[:, 0]), np.diff(outline[:, 1])))]
    step /= step[-1]
    at = np.linspace(0, 1, POINTS, endpoint=False)
    px = np.interp(at, step, outline[:, 0]) + x0
    py = np.interp(at, step, outline[:, 1]) + y0

    u = (px - cx) / scale
    v = (py - cy) / scale

    for x, y in zip(u, v):
        print(f'                    <StraightVertex x="{x:.2f}" y="{y:.2f}" />')

    print(
        f"# {POINTS} points, bbox {u.max() - u.min():.2f} x {v.max() - v.min():.2f} units, "
        f"centre ({((u.max() + u.min()) / 2):.2f}, {((v.max() + v.min()) / 2):.2f})",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
