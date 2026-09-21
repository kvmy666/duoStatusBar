"""Render traced StraightVertex lists as a PNG, to eyeball a trace before shipping it.

Usage: python tools/preview-vertices.py OUT.png IN1.txt [IN2.txt ...]
"""

import re
import sys

from PIL import Image, ImageDraw

PATTERN = re.compile(r'x="(-?[\d.]+)" y="(-?[\d.]+)"')


def main() -> None:
    out, sources = sys.argv[1], sys.argv[2:]
    tiles = []
    for path in sources:
        pts = [tuple(map(float, m)) for m in PATTERN.findall(open(path).read())]
        lo = min(min(p[0] for p in pts), min(p[1] for p in pts))
        hi = max(max(p[0] for p in pts), max(p[1] for p in pts))
        scale = 180.0 / max(hi - lo, 1e-6)
        tile = Image.new("L", (200, 200), 0)
        ImageDraw.Draw(tile).polygon(
            [((x - lo) * scale + 10, (y - lo) * scale + 10) for x, y in pts], fill=255
        )
        tiles.append(tile)
    sheet = Image.new("L", (200 * len(tiles), 200), 40)
    for i, tile in enumerate(tiles):
        sheet.paste(tile, (200 * i, 0))
    sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST).save(out)


if __name__ == "__main__":
    main()
