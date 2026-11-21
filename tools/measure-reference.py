#!/usr/bin/env python3
"""Measure the Duo reference screenshots so the design is read, not guessed.

Default (Duo) mode reports the ink bounding boxes relative to the whole sheet, so the numbers
transfer to the 120-unit design box regardless of the screenshot's scale.

    python tools/measure-reference.py docs/reference/duo-reference-01-percentage-off.jpg
    python tools/measure-reference.py docs/reference/duo-reference-02-percentage-50-16.jpg

`ios` mode measures an iOS status-bar cluster: it segments the ink, classifies the glyphs
(single bars or the dual stacked bars + dots, Wi-Fi, battery body/cap, clock digits) and
reports pixel bboxes, edges, gaps, corner radius and ink alpha. Everything is scaled to
design units with the battery body height as exactly 100 (the same convention as
`docs/ios-geometry.md`). It needs numpy + PIL only.

    python3 -I tools/measure-reference.py ios .pm/ref/ios-single-sim-light.png --mode single
    python3 -I tools/measure-reference.py ios .pm/ref/r2/out/crops/r6_rightcluster_10x.png \
        --px-scale 10 --origin 790,265 --mode single --json measurements.json

Polarity is detected from the background unless `--polarity` is given: `light` means a light
background with dark ink (ref 6), `dark` means a dark background with light ink (refs 1/5 and
the dual PNG). Crop boxes are `x0,y0,x1,y1` in source pixels, `x1`/`y1` exclusive.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

try:
    _FONT = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 18)
except Exception:
    _FONT = ImageFont.load_default()


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


# --------------------------------------------------------------------------------------
# iOS mode
# --------------------------------------------------------------------------------------


def _parse_box(text: str) -> tuple[int, int, int, int]:
    try:
        parts = [int(round(float(v))) for v in text.split(",")]
    except ValueError as exc:
        raise argparse.ArgumentTypeError(f"bad box {text!r}: {exc}") from exc
    if len(parts) != 4:
        raise argparse.ArgumentTypeError(f"bad box {text!r}: need x0,y0,x1,y1")
    x0, y0, x1, y1 = parts
    if x1 <= x0 or y1 <= y0:
        raise argparse.ArgumentTypeError(f"bad box {text!r}: empty")
    return x0, y0, x1, y1


def _load_ios(path: str) -> tuple[np.ndarray, np.ndarray, dict]:
    im = Image.open(path)
    im.load()
    src_mode = im.mode
    im = im.convert("RGBA")
    arr = np.asarray(im).astype(np.float64)
    rgb = arr[:, :, :3].copy()
    alpha = arr[:, :, 3]
    min_alpha = float(alpha.min()) if alpha.size else 255.0
    if min_alpha < 250.0:
        median_luma = float(np.median(rgb.mean(axis=2)))
        under = 255.0 if median_luma >= 128.0 else 0.0
        a = alpha[:, :, None] / 255.0
        rgb = rgb * a + under * (1.0 - a)
    luma = rgb @ np.array([0.299, 0.587, 0.114])
    meta = {
        "mode": src_mode,
        "alpha_min": min_alpha,
        "alpha_composited": min_alpha < 250.0,
    }
    return rgb, luma, meta


def _pick_polarity(luma: np.ndarray, forced: str) -> str:
    if forced != "auto":
        return forced
    return "light" if float(np.median(luma)) >= 128.0 else "dark"


def _weak_ink_mask(luma: np.ndarray, polarity: str) -> tuple[np.ndarray, float, float]:
    bg = float(np.median(luma))
    lo = float(np.percentile(luma, 1))
    hi = float(np.percentile(luma, 99))
    if polarity == "light":
        threshold = bg - max(8.0, 0.10 * (bg - lo))
        return luma < threshold, bg, threshold
    threshold = bg + max(8.0, 0.10 * (hi - bg))
    return luma > threshold, bg, threshold


def _dilate8(mask: np.ndarray) -> np.ndarray:
    out = mask.copy()
    out[1:, :] |= mask[:-1, :]
    out[:-1, :] |= mask[1:, :]
    out[:, 1:] |= mask[:, :-1]
    out[:, :-1] |= mask[:, 1:]
    out[1:, 1:] |= mask[:-1, :-1]
    out[1:, :-1] |= mask[:-1, 1:]
    out[:-1, 1:] |= mask[1:, :-1]
    out[:-1, :-1] |= mask[1:, 1:]
    return out


def _reconstruct(seed: np.ndarray, allowed: np.ndarray) -> np.ndarray:
    current = seed & allowed
    if not current.any():
        return current
    while True:
        nxt = _dilate8(current) & allowed
        if np.array_equal(nxt, current):
            return current
        current = nxt


def _components(mask: np.ndarray, min_area: int) -> list[dict]:
    h, w = mask.shape
    seen = np.zeros_like(mask, dtype=bool)
    comps: list[dict] = []
    for y0, x0 in zip(*np.nonzero(mask)):
        y0 = int(y0)
        x0 = int(x0)
        if seen[y0, x0]:
            continue
        stack = [(y0, x0)]
        seen[y0, x0] = True
        pts: list[tuple[int, int]] = []
        while stack:
            y, x = stack.pop()
            pts.append((y, x))
            for dy in (-1, 0, 1):
                ny = y + dy
                if ny < 0 or ny >= h:
                    continue
                for dx in (-1, 0, 1):
                    nx = x + dx
                    if nx < 0 or nx >= w or seen[ny, nx] or not mask[ny, nx]:
                        continue
                    seen[ny, nx] = True
                    stack.append((ny, nx))
        if len(pts) < min_area:
            continue
        py = np.array([p[0] for p in pts], dtype=np.int32)
        px = np.array([p[1] for p in pts], dtype=np.int32)
        comps.append(
            {
                "py": py,
                "px": px,
                "x0": int(px.min()),
                "x1": int(px.max()),
                "y0": int(py.min()),
                "y1": int(py.max()),
                "w": int(px.max() - px.min() + 1),
                "h": int(py.max() - py.min() + 1),
                "cx": float(px.mean()),
                "cy": float(py.mean()),
                "area": int(len(pts)),
            }
        )
    return comps


def _refine(comp: dict, luma: np.ndarray, bg: float, polarity: str, pad: int = 2) -> dict:
    h, w = luma.shape
    x0 = max(0, comp["x0"] - pad)
    x1 = min(w, comp["x1"] + pad + 1)
    y0 = max(0, comp["y0"] - pad)
    y1 = min(h, comp["y1"] + pad + 1)
    seed = np.zeros((y1 - y0, x1 - x0), dtype=bool)
    seed[comp["py"] - y0, comp["px"] - x0] = True
    dark_ink = polarity == "light"
    vals = luma[comp["py"], comp["px"]]
    fill = float(np.percentile(vals, 5 if dark_ink else 95))
    threshold = (bg + fill) / 2.0
    window = luma[y0:y1, x0:x1]
    allowed = window < threshold if dark_ink else window > threshold
    refined = _reconstruct(seed, allowed)
    ys, xs = np.nonzero(refined)
    if len(xs) == 0:
        refined = seed
        ys, xs = np.nonzero(refined)
    full = np.zeros_like(luma, dtype=bool)
    full[y0:y1, x0:x1] = refined
    core = luma[full]
    core_fill = float(np.percentile(core, 5 if dark_ink else 95))
    return {
        "mask": full,
        "py": comp["py"],
        "px": comp["px"],
        "x0": int(x0 + xs.min()),
        "x1": int(x0 + xs.max()),
        "y0": int(y0 + ys.min()),
        "y1": int(y0 + ys.max()),
        "w": int(xs.max() - xs.min() + 1),
        "h": int(ys.max() - ys.min() + 1),
        "cx": float(x0 + xs.mean()),
        "cy": float(y0 + ys.mean()),
        "area": int(len(xs)),
        "fill": core_fill,
    }


def _rgb_at_mask(rgb: np.ndarray, mask: np.ndarray) -> tuple[float, float, float]:
    pixels = rgb[mask]
    if pixels.size == 0:
        return (float("nan"),) * 3
    return tuple(float(v) for v in np.median(pixels, axis=0))


def _luma_at_mask(luma: np.ndarray, mask: np.ndarray) -> float:
    values = luma[mask]
    if values.size == 0:
        return float("nan")
    return float(np.median(values))


def _radius_from_area(w: int, h: int, area: int) -> float:
    excess = float(w * h - area)
    if excess <= 0:
        return 0.0
    return math.sqrt(excess / (4.0 - math.pi))


def _row_chain(comps: list[dict]) -> list[dict]:
    ordered = sorted(comps, key=lambda c: c["x0"])
    chains: list[list[dict]] = []
    for comp in ordered:
        if chains:
            prev = chains[-1][-1]
            gap = comp["x0"] - prev["x1"]
            if gap <= 1.2 * min(comp["w"], prev["w"]):
                chains[-1].append(comp)
                continue
        chains.append([comp])
    return max(chains, key=len) if chains else []


def _split_rows(comps: list[dict]) -> tuple[list[dict], list[dict]]:
    if len(comps) < 6:
        return sorted(comps, key=lambda c: c["x0"]), []
    ordered = sorted(comps, key=lambda c: c["cy"])
    heights = [c["h"] for c in ordered]
    median_h = float(np.median(heights))
    gaps = [
        (ordered[i + 1]["cy"] - ordered[i]["cy"], i)
        for i in range(len(ordered) - 1)
    ]
    gap, index = max(gaps) if gaps else (0.0, -1)
    if gap >= max(6.0, 0.45 * median_h):
        return (
            sorted(ordered[: index + 1], key=lambda c: c["x0"]),
            sorted(ordered[index + 1:], key=lambda c: c["x0"]),
        )
    return sorted(ordered, key=lambda c: c["x0"]), []


def _extend_body(comp: dict, luma: np.ndarray, bg: float, polarity: str) -> dict:
    """Union the strong fill with the uncharged remainder (a second, weaker ink level)."""
    if "py" not in comp:
        return comp
    seed = np.zeros_like(luma, dtype=bool)
    seed[comp["py"], comp["px"]] = True
    strong = comp["mask"]
    columns = np.arange(luma.shape[1], dtype=np.int32)[None, :]
    remainder_seed = seed & ~strong & (columns > comp["x1"] - 2)
    if int(remainder_seed.sum()) < 6:
        return comp
    dark_ink = polarity == "light"
    values = luma[remainder_seed]
    remainder_fill = float(np.percentile(values, 5 if dark_ink else 95))
    if abs(remainder_fill - bg) < 20.0:
        return comp
    core_threshold = (
        bg - 0.60 * (bg - remainder_fill) if dark_ink
        else bg + 0.60 * (remainder_fill - bg)
    )
    core = remainder_seed & (
        luma < core_threshold if dark_ink else luma > core_threshold
    )
    if int(core.sum()) < 6:
        return comp
    threshold = (bg + remainder_fill) / 2.0
    allowed = luma < threshold if dark_ink else luma > threshold
    remainder = _reconstruct(core, allowed)
    extra = remainder & ~strong
    if int(extra.sum()) < 6:
        return comp
    if int(extra.sum()) < 0.10 * int(strong.sum()):
        return comp
    union = strong | remainder
    ys, xs = np.nonzero(union)
    return {
        **comp,
        "mask": union,
        "x0": comp["x0"],
        "x1": int(max(comp["x1"], xs.max())),
        "y0": comp["y0"],
        "y1": comp["y1"],
        "w": int(xs.max() - xs.min() + 1),
        "h": comp["h"],
        "cx": float(xs.mean()),
        "cy": comp["cy"],
        "area": int(union.sum()),
        "remainder_fill": remainder_fill,
    }


def _split_side_by_side(comps: list[dict]) -> tuple[list[dict], list[dict]]:
    """Detect the on-device bug layout: 4 compressed bars followed by 4 dots on one row."""
    ordered = sorted(comps, key=lambda c: c["x0"])
    if len(ordered) < 8 or len(ordered) % 4 != 0:
        return ordered, []
    half = len(ordered) // 2
    first, second = ordered[:half], ordered[half:]
    tall = max((c["h"] / max(1.0, c["w"])) for c in first)
    dots = max((c["h"] / max(1.0, c["w"])) for c in second)
    if tall > 1.5 and dots <= 1.4:
        return first, second
    return ordered, []


def _classify(comps: list[dict], mode: str) -> dict:
    comps = [c for c in comps if c["area"] >= 10]
    if not comps:
        return {}
    body = max(comps, key=lambda c: c["area"])
    rest = [c for c in comps if c is not body]
    cap = None
    cap_candidates = [
        c
        for c in rest
        if c is not body
        and c["x0"] >= body["x1"] - 1
        and c["x0"] <= body["x1"] + 0.4 * body["w"]
        and c["w"] <= max(2, 0.30 * body["w"])
        and c["h"] <= 0.80 * body["h"]
        and min(c["y1"], body["y1"]) - max(c["y0"], body["y0"]) + 1 >= 0.40 * c["h"]
    ]
    if cap_candidates:
        cap = max(cap_candidates, key=lambda c: c["w"] * c["h"])
    rest = [c for c in rest if c is not cap]

    band_lo = body["y0"] - 0.60 * body["h"]
    band_hi = body["y1"] + 0.60 * body["h"]
    left = [c for c in rest if c["x1"] < body["x0"] - 1]
    ignored = [c for c in rest if c["x1"] >= body["x0"] - 1]
    band = [c for c in left if band_lo <= c["cy"] <= band_hi]
    ignored.extend([c for c in left if not any(c is b for b in band)])

    wifi_comps: list[dict] = []
    if len(band) > 4:
        widths = sorted(c["w"] for c in band)
        median_w = widths[len(widths) // 2]
        outer = max(band, key=lambda c: c["w"])
        if outer["w"] >= 2.5 * median_w:
            wifi_comps = [
                c for c in band if outer["x0"] <= c["cx"] <= outer["x1"]
            ]
    cell_pool = [c for c in band if not any(c is w for w in wifi_comps)]
    cell_chain = _row_chain(cell_pool)
    if not wifi_comps:
        others = [c for c in band if not any(c is b for b in cell_chain)]
        wifi_comps = [c for c in others if not cell_chain or c["x0"] > cell_chain[-1]["x1"]]
        ignored.extend([c for c in others if not any(c is w for w in wifi_comps)])

    if mode == "single":
        bars = sorted(cell_chain, key=lambda c: c["x0"])
        dots: list[dict] = []
    elif mode == "dual":
        top, bottom = _split_rows(cell_chain)
        bars, dots = top, bottom
        if not bottom:
            bars, dots = _split_side_by_side(cell_chain)
    else:
        top, bottom = _split_rows(cell_chain)
        if bottom:
            bars, dots = top, bottom
        else:
            bars, dots = _split_side_by_side(cell_chain)
            if not dots:
                bars, dots = sorted(cell_chain, key=lambda c: c["x0"]), []

    return {
        "body": body,
        "cap": cap,
        "cell_chain": cell_chain,
        "bars": bars,
        "dots": dots,
        "wifi": sorted(wifi_comps, key=lambda c: c["x0"]),
        "ignored": ignored,
    }


def _fmt(value: float | None, digits: int = 1) -> str:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return "-"
    return f"{value:.{digits}f}"


def _metrics_for_bars(bars: list[dict]) -> dict:
    bars = sorted(bars, key=lambda c: c["x0"])
    if not bars:
        return {}
    widths = [float(c["w"]) for c in bars]
    heights = [float(c["h"]) for c in bars]
    lefts = [float(c["x0"]) for c in bars]
    rights = [float(c["x1"]) for c in bars]
    bottoms = [float(c["y1"]) for c in bars]
    tops = [float(c["y0"]) for c in bars]
    gaps = [lefts[i + 1] - rights[i] - 1 for i in range(len(bars) - 1)]
    pitches = [lefts[i + 1] - lefts[i] for i in range(len(bars) - 1)]
    radii = [
        min(c["w"] / 2.0, _radius_from_area(c["w"], c["h"], c["area"]))
        for c in bars
    ]
    bottom_spread = max(bottoms) - min(bottoms)
    return {
        "count": len(bars),
        "bbox_px": [min(lefts), min(tops), max(rights), max(bottoms)],
        "widths_px": widths,
        "heights_px": heights,
        "lefts_px": lefts,
        "tops_px": tops,
        "bottoms_px": bottoms,
        "gaps_px": gaps,
        "pitches_px": pitches,
        "radius_px": radii,
        "radius_over_width": [r / w for r, w in zip(radii, widths)],
        "bottom_spread_px": bottom_spread,
        "block_w_px": max(rights) - min(lefts) + 1.0,
        "block_h_px": max(bottoms) - min(tops) + 1.0,
    }


def _metrics_for_dots(dots: list[dict]) -> dict:
    dots = sorted(dots, key=lambda c: c["x0"])
    if not dots:
        return {}
    lefts = [float(c["x0"]) for c in dots]
    rights = [float(c["x1"]) for c in dots]
    widths = [float(c["w"]) for c in dots]
    heights = [float(c["h"]) for c in dots]
    tops = [float(c["y0"]) for c in dots]
    bottoms = [float(c["y1"]) for c in dots]
    gaps = [lefts[i + 1] - rights[i] - 1 for i in range(len(dots) - 1)]
    pitches = [lefts[i + 1] - lefts[i] for i in range(len(dots) - 1)]
    return {
        "count": len(dots),
        "bbox_px": [min(lefts), min(tops), max(rights), max(bottoms)],
        "lefts_px": lefts,
        "tops_px": tops,
        "widths_px": widths,
        "heights_px": heights,
        "gaps_px": gaps,
        "pitches_px": pitches,
        "block_w_px": max(rights) - min(lefts) + 1.0,
        "block_h_px": max(bottoms) - min(tops) + 1.0,
    }


def _battery_split(body: dict, luma: np.ndarray, bg: float, polarity: str) -> dict:
    mask = body["mask"]
    y0, y1 = body["y0"], body["y1"]
    height = y1 - y0 + 1
    rows = range(y0 + int(0.30 * height), y0 + int(0.70 * height) + 1)
    columns = {}
    for x in range(body["x0"], body["x1"] + 1):
        vals = [luma[y, x] for y in rows if mask[y, x]]
        if vals:
            columns[x] = float(np.median(vals))
    if not columns:
        return {}
    xs = sorted(columns)
    values = np.array([columns[x] for x in xs])
    if polarity == "dark":
        fill_extreme = float(np.percentile(values, 95))
        other_extreme = float(np.percentile(values, 5))
    else:
        fill_extreme = float(np.percentile(values, 5))
        other_extreme = float(np.percentile(values, 95))
    spread = abs(fill_extreme - other_extreme)
    if spread < 12.0:
        split_index = len(xs) - 1
        fill_fraction = 1.0
        remainder_luma = None
    else:
        mid = (fill_extreme + other_extreme) / 2.0
        if polarity == "dark":
            filled = values >= mid
        else:
            filled = values <= mid
        split_index = int(np.nonzero(filled)[0].max()) if filled.any() else 0
        fill_fraction = (xs[split_index] - body["x0"] + 1) / (body["x1"] - body["x0"] + 1)
        remainder_luma = float(np.median(values[split_index + 1:])) if split_index + 1 < len(xs) else None
    return {
        "fill_extreme": fill_extreme,
        "other_extreme": other_extreme,
        "fill_fraction": fill_fraction,
        "split_x_px": float(xs[split_index]) if xs else None,
        "remainder_luma": remainder_luma,
    }


def _clock_metrics(
    rgb: np.ndarray,
    luma: np.ndarray,
    clock_crop: tuple[int, int, int, int] | None,
    polarity: str,
    max_saturation: float = 60.0,
) -> dict:
    if clock_crop is None:
        return {}
    x0, y0, x1, y1 = clock_crop
    sub_luma = luma[y0:y1, x0:x1]
    sub_rgb = rgb[y0:y1, x0:x1]
    bg = float(np.median(sub_luma))
    dark_ink = polarity == "light"
    lo = float(np.percentile(sub_luma, 1))
    hi = float(np.percentile(sub_luma, 99))
    if dark_ink:
        threshold = bg - max(8.0, 0.10 * (bg - lo))
        weak = sub_luma < threshold
    else:
        threshold = bg + max(8.0, 0.10 * (hi - bg))
        weak = sub_luma > threshold
    comps = _components(weak, 8)
    digits: list[dict] = []
    rejected: list[dict] = []
    for comp in comps:
        pixels = sub_rgb[comp["py"], comp["px"]]
        sat = float(np.median(np.max(pixels, axis=1) - np.min(pixels, axis=1)))
        patch = sub_luma[comp["py"], comp["px"]]
        fill = float(np.percentile(patch, 5 if dark_ink else 95))
        contrast = abs(fill - bg)
        if sat > max_saturation or contrast < 40.0:
            rejected.append({**comp, "saturation": sat, "contrast": contrast})
            continue
        digits.append(_refine(comp, sub_luma, bg, polarity))
    if not digits:
        return {"crop": clock_crop, "digits": [], "cap_height_px": None}
    max_h = max(c["h"] for c in digits)
    tall = [c for c in digits if c["h"] >= 0.55 * max_h]
    if len(tall) > 1:
        centers = sorted(c["cy"] for c in tall)
        median_cy = centers[len(centers) // 2]
        median_h = sorted(c["h"] for c in tall)[len(tall) // 2]
        tall = [
            c for c in tall
            if abs(c["cy"] - median_cy) <= max(8.0, 0.80 * median_h)
        ]
    tops = [c["y0"] for c in tall]
    bottoms = [c["y1"] for c in tall]
    cap_height = float(max(bottoms) - min(tops) + 1)
    heights = [float(c["h"]) for c in tall]
    return {
        "crop": clock_crop,
        "digits": [
            {
                "bbox_px": [float(c["x0"] + x0), float(c["y0"] + y0),
                            float(c["x1"] + x0), float(c["y1"] + y0)],
                "w_px": float(c["w"]),
                "h_px": float(c["h"]),
            }
            for c in sorted(tall, key=lambda c: c["x0"])
        ],
        "rejected": len(rejected),
        "cap_height_px": cap_height,
        "digit_heights_px": heights,
        "top_px": float(min(tops) + y0),
        "bottom_px": float(max(bottoms) + y0),
    }


def _units(value_px: float | None, px_per_unit: float) -> float | None:
    if value_px is None or px_per_unit <= 0:
        return None
    return value_px / px_per_unit


def _downsampled_rgb(path: str, scale: float) -> np.ndarray:
    image = Image.open(path).convert("RGB")
    if scale > 1.0:
        image = image.resize(
            (max(1, round(image.width / scale)), max(1, round(image.height / scale))),
            Image.BOX,
        )
    return np.asarray(image).astype(np.float64)


def _luma(rgb: np.ndarray) -> np.ndarray:
    return rgb @ np.array([0.299, 0.587, 0.114])


def measure_ios_clock_only(path: str, args: argparse.Namespace) -> dict:
    rgb, _orig_luma, meta = _load_ios(path)
    height, width = rgb.shape[:2]
    scale = args.px_scale
    native_rgb = _downsampled_rgb(path, scale)
    native_luma = _luma(native_rgb)
    source_crop = args.clock_crop if args.clock_crop else (0, 0, width, height)
    crop = tuple(int(round(v / scale)) for v in source_crop)
    polarity = _pick_polarity(native_luma, args.polarity)
    clock = _clock_metrics(native_rgb, native_luma, crop, polarity, args.max_saturation)
    body_h = args.body_height_px
    px_per_unit = body_h / 100.0 if body_h else None
    ratios = {}
    if body_h and clock.get("cap_height_px"):
        ratios["clock_cap_over_battery_body"] = clock["cap_height_px"] / body_h
        ratios["battery_body_over_clock_cap"] = body_h / clock["cap_height_px"]
    result = {
        "source": os.path.abspath(path),
        "source_name": os.path.basename(path),
        "source_bytes": os.path.getsize(path),
        "source_mtime": os.path.getmtime(path),
        "source_size": [width, height],
        "source_meta": meta,
        "crop": list(source_crop),
        "crop_native": list(crop),
        "crop_origin": args.origin if args.origin else [source_crop[0], source_crop[1]],
        "px_scale": scale,
        "px_space": "native px = source px / px_scale",
        "mode_requested": "clock-only",
        "polarity": polarity,
        "battery_body_h_px": body_h,
        "px_per_unit": px_per_unit,
        "units": 100.0,
        "clock": clock,
        "ratios": ratios,
    }
    if args.confidence:
        result["confidence"] = args.confidence
    return result


def _shift_result(result: dict, dx: int, dy: int) -> None:
    body = result.get("body") or {}
    for key in ("body_bbox_px", "cap_bbox_px"):
        if key in body:
            v = body[key]
            body[key] = [v[0] + dx, v[1] + dy, v[2] + dx, v[3] + dy]
    split = body.get("split") or {}
    if split.get("split_x_px") is not None:
        split["split_x_px"] += dx
    cell = result.get("cell") or {}
    if cell:
        cell["bbox_px"] = [cell["bbox_px"][0] + dx, cell["bbox_px"][1] + dy,
                           cell["bbox_px"][2] + dx, cell["bbox_px"][3] + dy]
        cell["lefts_px"] = [v + dx for v in cell["lefts_px"]]
        cell["tops_px"] = [v + dy for v in cell["tops_px"]]
        cell["bottoms_px"] = [v + dy for v in cell["bottoms_px"]]
    dots = result.get("dots") or {}
    if dots:
        dots["bbox_px"] = [dots["bbox_px"][0] + dx, dots["bbox_px"][1] + dy,
                           dots["bbox_px"][2] + dx, dots["bbox_px"][3] + dy]
        dots["lefts_px"] = [v + dx for v in dots["lefts_px"]]
        dots["tops_px"] = [v + dy for v in dots["tops_px"]]
    wifi = result.get("wifi") or {}
    if wifi:
        wifi["bbox_px"] = [wifi["bbox_px"][0] + dx, wifi["bbox_px"][1] + dy,
                           wifi["bbox_px"][2] + dx, wifi["bbox_px"][3] + dy]
        outer = wifi.get("outer_bbox_px")
        if outer:
            wifi["outer_bbox_px"] = [outer[0] + dx, outer[1] + dy, outer[2] + dx, outer[3] + dy]
    for comp in result.get("components", []):
        b = comp["bbox_px"]
        comp["bbox_px"] = [b[0] + dx, b[1] + dy, b[2] + dx, b[3] + dy]


def measure_ios_one(path: str, args: argparse.Namespace) -> dict:
    rgb, luma, meta = _load_ios(path)
    height, width = luma.shape
    source_crop = args.crop if args.crop else (0, 0, width, height)
    x0, y0, x1, y1 = source_crop
    x1 = min(x1, width)
    y1 = min(y1, height)
    if x1 <= x0 or y1 <= y0:
        raise SystemExit(f"crop {source_crop} is outside {path} ({width}x{height})")
    scale = args.px_scale
    native_rgb = _downsampled_rgb(path, scale)
    native_luma = _luma(native_rgb)
    nx0, ny0 = int(round(x0 / scale)), int(round(y0 / scale))
    nx1, ny1 = int(round(x1 / scale)), int(round(y1 / scale))
    nx1 = min(nx1, native_rgb.shape[1])
    ny1 = min(ny1, native_rgb.shape[0])
    work_rgb = native_rgb[ny0:ny1, nx0:nx1]
    work_luma = native_luma[ny0:ny1, nx0:nx1]
    polarity = _pick_polarity(work_luma, args.polarity)
    weak, bg, threshold = _weak_ink_mask(work_luma, polarity)
    comps_raw = _components(weak, args.min_area)
    comps = [_refine(c, work_luma, bg, polarity) for c in comps_raw]
    contrast = [abs(c["fill"] - bg) for c in comps]
    strong = max(contrast) if contrast else 1.0
    for comp in comps:
        comp["alpha"] = abs(comp["fill"] - bg) / strong if strong > 1e-6 else None
        comp["rgb"] = _rgb_at_mask(work_rgb, comp["mask"])
        comp["saturation"] = (
            float(
                np.median(
                    np.max(work_rgb[comp["mask"]], axis=1)
                    - np.min(work_rgb[comp["mask"]], axis=1)
                )
            )
            if comp["mask"].any()
            else 0.0
        )

    neutral = [c for c in comps if c["saturation"] <= args.max_saturation]
    saturated = [c for c in comps if c["saturation"] > args.max_saturation]
    classification = _classify(neutral, args.mode)
    classification.setdefault("ignored", []).extend(saturated)
    body = classification.get("body")
    cap = classification.get("cap")
    if body is None:
        raise SystemExit(f"no glyphs found in {path} crop {source_crop}")
    body = _extend_body(body, work_luma, bg, polarity)
    classification["body"] = body

    body_h = float(body["h"])
    px_per_unit = body_h / 100.0 if body_h > 0 else 0.0
    bars = classification.get("bars") or []
    dots = classification.get("dots") or []
    wifi_comps = classification.get("wifi") or []

    cells = _metrics_for_bars(bars)
    dots_metrics = _metrics_for_dots(dots)
    if cells:
        cells["widths_units"] = [_units(v, px_per_unit) for v in cells["widths_px"]]
        cells["heights_units"] = [_units(v, px_per_unit) for v in cells["heights_px"]]
        cells["pitch_units"] = [_units(v, px_per_unit) for v in cells["pitches_px"]]
        cells["gap_units"] = [_units(v, px_per_unit) for v in cells["gaps_px"]]
        cells["radius_units"] = [_units(v, px_per_unit) for v in cells["radius_px"]]
        cells["alphas"] = [c.get("alpha") for c in bars]
        cells["bar_dot_gap_px"] = (
            dots_metrics["bbox_px"][1] - cells["bbox_px"][3] - 1
            if dots_metrics and dots
            else None
        )
        cells["layout"] = (
            "single" if not dots
            else ("side-by-side" if cells["bar_dot_gap_px"] < 0 else "stacked")
        )
        cells["bar_dot_gap_units"] = (
            _units(cells["bar_dot_gap_px"], px_per_unit)
            if cells["bar_dot_gap_px"] is not None
            else None
        )
    if dots_metrics:
        dots_metrics["widths_units"] = [_units(v, px_per_unit) for v in dots_metrics["widths_px"]]
        dots_metrics["heights_units"] = [_units(v, px_per_unit) for v in dots_metrics["heights_px"]]
        dots_metrics["pitch_units"] = [_units(v, px_per_unit) for v in dots_metrics["pitches_px"]]
        dots_metrics["gap_units"] = [_units(v, px_per_unit) for v in dots_metrics["gaps_px"]]
        dots_metrics["alphas"] = [c.get("alpha") for c in sorted(dots, key=lambda c: c["x0"])]

    wifi = {}
    if wifi_comps:
        wx0 = float(min(c["x0"] for c in wifi_comps))
        wx1 = float(max(c["x1"] for c in wifi_comps))
        wy0 = float(min(c["y0"] for c in wifi_comps))
        wy1 = float(max(c["y1"] for c in wifi_comps))
        outer = max(wifi_comps, key=lambda c: c["w"] * c["h"])
        wifi = {
            "bbox_px": [wx0, wy0, wx1, wy1],
            "w_px": wx1 - wx0 + 1.0,
            "h_px": wy1 - wy0 + 1.0,
            "w_units": _units(wx1 - wx0 + 1.0, px_per_unit),
            "h_units": _units(wy1 - wy0 + 1.0, px_per_unit),
            "outer_bbox_px": [float(outer["x0"]), float(outer["y0"]),
                              float(outer["x1"]), float(outer["y1"])],
            "alphas": [c.get("alpha") for c in wifi_comps],
        }

    split_metrics = _battery_split(body, work_luma, bg, polarity)
    full_body = float(split_metrics.get("fill_fraction", 1.0)) >= 0.98
    body_radius = (
        min(
            body["w"] / 2.0,
            min(body["h"] / 2.0, _radius_from_area(body["w"], body["h"], body["area"])),
        )
        if full_body
        else None
    )
    battery = {
        "body_bbox_px": [float(body["x0"]), float(body["y0"]),
                         float(body["x1"]), float(body["y1"])],
        "body_w_px": float(body["w"]),
        "body_h_px": body_h,
        "body_w_units": _units(float(body["w"]), px_per_unit),
        "body_radius_px": body_radius,
        "body_radius_units": _units(body_radius, px_per_unit) if body_radius else None,
        "body_radius_note": "area fit; only for a fully charged body" if body_radius is None else None,
        "body_alpha": body.get("alpha"),
        "body_rgb": body.get("rgb"),
        "split": split_metrics,
    }
    if cap is not None:
        battery["cap_bbox_px"] = [float(cap["x0"]), float(cap["y0"]),
                                  float(cap["x1"]), float(cap["y1"])]
        battery["cap_w_px"] = float(cap["w"])
        battery["cap_h_px"] = float(cap["h"])
        battery["cap_w_units"] = _units(float(cap["w"]), px_per_unit)
        battery["cap_h_units"] = _units(float(cap["h"]), px_per_unit)
        battery["cap_gap_px"] = float(cap["x0"] - body["x1"] - 1)
        battery["cap_gap_units"] = _units(battery["cap_gap_px"], px_per_unit)
        battery["cap_alpha"] = cap.get("alpha")
        battery["cap_rgb"] = cap.get("rgb")
        battery["cap_luma"] = cap.get("fill")
        battery["cap_vs_body_alpha"] = (
            cap.get("alpha") / body["alpha"] if body.get("alpha") else None
        )

    clock_crop = None
    if args.clock_crop:
        clock_crop = tuple(int(round(v / scale)) for v in args.clock_crop)
    clock = _clock_metrics(
        native_rgb, native_luma, clock_crop,
        _pick_polarity(native_luma, args.polarity), args.max_saturation,
    )
    clock_cap_px = clock.get("cap_height_px") if clock else None
    ratios: dict[str, float | None] = {}
    if clock_cap_px:
        ratios["clock_cap_over_battery_body"] = clock_cap_px / body_h if body_h else None
        if wifi:
            ratios["wifi_h_over_clock_cap"] = wifi["h_px"] / clock_cap_px
            ratios["wifi_w_over_clock_cap"] = wifi["w_px"] / clock_cap_px
        ratios["battery_body_over_clock_cap"] = body_h / clock_cap_px
        ratios["battery_cap_h_over_clock_cap"] = (
            battery.get("cap_h_px") / clock_cap_px if battery.get("cap_h_px") else None
        )
        if cells:
            ratios["cell_block_h_over_clock_cap"] = cells["block_h_px"] / clock_cap_px
            ratios["bar4_h_over_clock_cap"] = cells["heights_px"][-1] / clock_cap_px
    if wifi and body_h:
        ratios["wifi_h_over_battery_body"] = wifi["h_px"] / body_h
        ratios["wifi_w_over_battery_body"] = wifi["w_px"] / body_h
    if cells and body_h:
        ratios["cell_block_h_over_battery_body"] = cells["block_h_px"] / body_h
        ratios["bar4_h_over_battery_body"] = cells["heights_px"][-1] / body_h

    shifted = {
        "body": battery,
        "cell": cells,
        "dots": dots_metrics,
        "wifi": wifi,
        "components": [
            {
                "bbox_px": [float(c["x0"]), float(c["y0"]), float(c["x1"]), float(c["y1"])],
                "w_px": float(c["w"]),
                "h_px": float(c["h"]),
                "area_px": float(c["area"]),
                "fill_luma": c["fill"],
                "alpha": c["alpha"],
                "rgb": c["rgb"],
                "saturation": c["saturation"],
            }
            for c in sorted(comps, key=lambda c: (c["x0"], c["y0"]))
        ],
    }
    _shift_result(shifted, nx0, ny0)

    result = {
        "source": os.path.abspath(path),
        "source_name": os.path.basename(path),
        "source_bytes": os.path.getsize(path),
        "source_mtime": os.path.getmtime(path),
        "source_size": [width, height],
        "source_meta": meta,
        "crop": list(source_crop),
        "crop_native": [nx0, ny0, nx1, ny1],
        "crop_origin": args.origin if args.origin else [x0, y0],
        "px_scale": scale,
        "px_space": "native px = source px / px_scale",
        "mode_requested": args.mode,
        "mode_detected": "dual" if dots else "single",
        "polarity": polarity,
        "background_luma": bg,
        "weak_threshold": threshold,
        "battery_body_h_px": body_h,
        "px_per_unit": px_per_unit,
        "units": 100.0,
        "body": shifted["body"],
        "cell": shifted["cell"],
        "dots": shifted["dots"],
        "wifi": shifted["wifi"],
        "clock": clock,
        "ratios": ratios,
        "components": shifted["components"],
        "ignored_components": len(classification.get("ignored") or []),
    }
    if args.confidence:
        result["confidence"] = args.confidence
    return result


def _print_ios_report(result: dict, dump: bool) -> None:
    src = result["source_name"]
    print(
        f"\n### {src}  {result['source_size'][0]}x{result['source_size'][1]}"
        f"  crop={tuple(result['crop'])}  scale={result['px_scale']:g}"
    )
    print(
        f"polarity={result['polarity']}  bg={result['background_luma']:.0f}"
        f"  weak_thr={result['weak_threshold']:.0f}"
        f"  battery_h={result['battery_body_h_px']:.2f}px"
        f"  px/unit={result['px_per_unit']:.4f}"
        f"  mode={result['mode_detected']}"
    )
    body = result["body"]
    print(
        "BATTERY body  bbox=(%.1f,%.1f)-(%.1f,%.1f)  WxH=%.2f x %.2f px"
        "  units=%.2f x 100.00  r=%.2f px"
        % (
            body["body_bbox_px"][0], body["body_bbox_px"][1],
            body["body_bbox_px"][2], body["body_bbox_px"][3],
            body["body_w_px"], body["body_h_px"],
            body["body_w_units"] or 0.0,
            body["body_radius_px"] if body["body_radius_px"] is not None else -1.0,
        )
    )
    if "cap_w_px" in body:
        print(
            "BATTERY cap   bbox=(%.1f,%.1f)-(%.1f,%.1f)  WxH=%.2f x %.2f px"
            "  units=%.2f x %.2f  gap=%.2f px  alpha=%.3f  rgb=(%.0f,%.0f,%.0f)"
            % (
                body["cap_bbox_px"][0], body["cap_bbox_px"][1],
                body["cap_bbox_px"][2], body["cap_bbox_px"][3],
                body["cap_w_px"], body["cap_h_px"],
                body["cap_w_units"] or 0.0, body["cap_h_units"] or 0.0,
                body["cap_gap_px"], body["cap_alpha"] or 0.0,
                *(body["cap_rgb"] or (0, 0, 0)),
            )
        )
        print(
            "BATTERY       body alpha=%.3f rgb=(%.0f,%.0f,%.0f)"
            "  cap/body alpha=%.2f  split=%.1f%%  remainder_luma=%s"
            % (
                body["body_alpha"] or 0.0, *(body["body_rgb"] or (0, 0, 0)),
                body["cap_vs_body_alpha"] or 0.0,
                100.0 * body["split"].get("fill_fraction", 0.0),
                _fmt(body["split"].get("remainder_luma"), 1),
            )
        )
    cell = result.get("cell") or {}
    if cell:
        print("\nCELL (bars)   index    bbox px              w      h    left   top   bottom  r    r/w   alpha")
        for i in range(cell["count"]):
            print(
                "              %5d  (%6.1f,%6.1f)-(%6.1f,%6.1f) %6.2f %6.2f %6.2f %6.2f %6.2f %5.2f %5.3f %5.3f"
                % (
                    i + 1,
                    cell["lefts_px"][i], cell["tops_px"][i],
                    cell["lefts_px"][i] + cell["widths_px"][i], cell["tops_px"][i] + cell["heights_px"][i],
                    cell["widths_px"][i], cell["heights_px"][i],
                    cell["lefts_px"][i], cell["tops_px"][i], cell["bottoms_px"][i],
                    cell["radius_px"][i], cell["radius_over_width"][i],
                    cell["alphas"][i] if cell["alphas"][i] is not None else -1.0,
                )
            )
        print(
            "              gaps=%s  pitch=%s  block=%.2fx%.2f px  bottom_spread=%.2f"
            % (
                ["%.2f" % v for v in cell["gaps_px"]],
                ["%.2f" % v for v in cell["pitches_px"]],
                cell["block_w_px"], cell["block_h_px"], cell["bottom_spread_px"],
            )
        )
        print(
            "              units: w=%s h=%s pitch=%s gap=%s r=%s"
            % (
                ["%.2f" % v for v in cell["widths_units"]],
                ["%.2f" % v for v in cell["heights_units"]],
                ["%.2f" % v for v in cell["pitch_units"]],
                ["%.2f" % v for v in cell["gap_units"]],
                ["%.2f" % v for v in cell["radius_units"]],
            )
        )
    dots = result.get("dots") or {}
    if dots:
        print("\nCELL (dots)   index    bbox px              w      h    alpha")
        for i in range(dots["count"]):
            print(
                "              %5d  left=%6.2f top=%6.2f  %6.2f %6.2f  %5.3f"
                % (i + 1, dots["lefts_px"][i], dots["tops_px"][i], dots["widths_px"][i],
                   dots["heights_px"][i], dots["alphas"][i] or -1.0)
            )
        print(
            "              gaps=%s pitch=%s block=%.2fx%.2f px"
            % (
                ["%.2f" % v for v in dots["gaps_px"]],
                ["%.2f" % v for v in dots["pitches_px"]],
                dots["block_w_px"], dots["block_h_px"],
            )
        )
    if cell and dots:
        print(
            "              row gap = %.2f px (%.2f units)"
            % (cell["bar_dot_gap_px"], cell["bar_dot_gap_units"] or 0.0)
        )
    wifi = result.get("wifi") or {}
    if wifi:
        print(
            "\nWIFI          bbox=(%.1f,%.1f)-(%.1f,%.1f)  WxH=%.2f x %.2f px"
            "  units=%.2f x %.2f"
            % (
                wifi["bbox_px"][0], wifi["bbox_px"][1], wifi["bbox_px"][2], wifi["bbox_px"][3],
                wifi["w_px"], wifi["h_px"], wifi["w_units"] or 0.0, wifi["h_units"] or 0.0,
            )
        )
    clock = result.get("clock") or {}
    if clock and clock.get("cap_height_px"):
        print(
            "\nCLOCK         cap height=%.2f px  digit heights=%s  top=%.1f bottom=%.1f"
            % (
                clock["cap_height_px"],
                ["%.2f" % v for v in clock["digit_heights_px"]],
                clock["top_px"], clock["bottom_px"],
            )
        )
        if clock.get("digits"):
            print(
                "              digit bboxes=%s"
                % ["(%.1f,%.1f)-(%.1f,%.1f)" % tuple(d["bbox_px"]) for d in clock["digits"]]
            )
    if result["ratios"]:
        print("\nRATIOS")
        for key, value in result["ratios"].items():
            print("              %-34s %s" % (key, _fmt(value, 3)))
    if dump:
        print("\nCOMPONENTS (refined 50% masks)")
        for i, comp in enumerate(result["components"]):
            print(
                "              #%02d bbox=(%.1f,%.1f)-(%.1f,%.1f) %5.2fx%5.2f area=%6.2f"
                " fill=%6.1f alpha=%.3f rgb=(%.0f,%.0f,%.0f) sat=%.1f"
                % (
                    i + 1, comp["bbox_px"][0], comp["bbox_px"][1],
                    comp["bbox_px"][2], comp["bbox_px"][3],
                    comp["w_px"], comp["h_px"], comp["area_px"], comp["fill_luma"],
                    comp["alpha"] if comp["alpha"] is not None else -1.0,
                    *comp["rgb"], comp["saturation"],
                )
            )


def _annotate(result: dict, out_path: str) -> None:
    src = result["source"]
    scale = result["px_scale"]
    image = _downsampled_rgb(src, scale)
    nx0, ny0, nx1, ny1 = result["crop_native"]
    image = Image.fromarray(image.astype(np.uint8)[ny0:ny1, nx0:nx1])
    zoom = 4
    image = image.resize((image.width * zoom, image.height * zoom), Image.NEAREST)
    draw = ImageDraw.Draw(image)

    def box(values, colour, label):
        bx0, by0, bx1, by1 = [
            (values[0] - nx0) * zoom, (values[1] - ny0) * zoom,
            (values[2] - nx0) * zoom, (values[3] - ny0) * zoom,
        ]
        draw.rectangle([bx0, by0, bx1, by1], outline=colour, width=2)
        draw.text((bx0 + 3, max(0, by0 - 17)), label, fill=colour, font=_FONT)

    body = result.get("body") or {}
    if body:
        box([body["body_bbox_px"][0], body["body_bbox_px"][1],
             body["body_bbox_px"][2], body["body_bbox_px"][3]], (255, 64, 64), "BAT")
    if "cap_bbox_px" in body:
        box(body["cap_bbox_px"], (255, 160, 0), "CAP")
    cell = result.get("cell") or {}
    for i in range(cell.get("count", 0)):
        left = cell["lefts_px"][i]
        top = cell["tops_px"][i]
        box([left, top, left + cell["widths_px"][i], top + cell["heights_px"][i]],
            (0, 200, 255), f"B{i + 1}")
    dots = result.get("dots") or {}
    if dots.get("count"):
        for i in range(dots["count"]):
            left = dots["lefts_px"][i]
            top = dots["tops_px"][i]
            box([left, top, left + dots["widths_px"][i], top + dots["heights_px"][i]],
                (0, 255, 120), f"D{i + 1}")
    wifi = result.get("wifi") or {}
    if wifi:
        box(wifi["bbox_px"], (180, 0, 255), "WIFI")
    clock = result.get("clock") or {}
    for i, digit in enumerate(clock.get("digits") or []):
        box(digit["bbox_px"], (0, 120, 255), f"C{i + 1}")
    image.save(out_path)


def ios_main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        prog="measure-reference.py ios",
        description="Measure iOS status-bar glyphs; battery body height = 100 units.",
    )
    parser.add_argument("images", nargs="+", help="reference image(s)")
    parser.add_argument("--crop", type=_parse_box, default=None,
                        help="analysis box x0,y0,x1,y1 in source pixels (x1/y1 exclusive)")
    parser.add_argument("--clock-crop", type=_parse_box, default=None,
                        help="clock box in source pixels, measured in the same source scale")
    parser.add_argument("--mode", choices=("auto", "single", "dual"), default="auto",
                        help="cell layout; auto splits the two stacked rows")
    parser.add_argument("--polarity", choices=("auto", "light", "dark"), default="auto",
                        help="light = light background/dark ink")
    parser.add_argument("--px-scale", type=float, default=1.0,
                        help="source pixels per native pixel (10 for the 10x crops)")
    parser.add_argument("--origin", type=str, default=None,
                        help="origin of a crop inside the original capture, x,y (recorded only)")
    parser.add_argument("--clock-only", action="store_true",
                        help="measure only --clock-crop (no battery glyph in the image)")
    parser.add_argument("--body-height-px", type=float, default=None,
                        help="battery body height in native px, for clock-only units/ratios")
    parser.add_argument("--min-area", type=int, default=10)
    parser.add_argument("--max-saturation", type=float, default=60.0,
                        help="components with a wider RGB spread are annotation ink, not glyphs")
    parser.add_argument("--confidence", choices=("high", "medium", "low"), default=None,
                        help="recorded verdict for this source")
    parser.add_argument("--json", default=None, help="write all results to this JSON file")
    parser.add_argument("--annotate", default=None,
                        help="write an annotated PNG here (single image only)")
    parser.add_argument("--dump", action="store_true", help="print every refined component")
    args = parser.parse_args(argv)
    if args.origin:
        try:
            ox, oy = [int(v) for v in args.origin.split(",")]
            args.origin = [ox, oy]
        except ValueError as exc:
            raise SystemExit(f"bad --origin {args.origin!r}: {exc}") from exc

    results = []
    for path in args.images:
        result = (
            measure_ios_clock_only(path, args) if args.clock_only
            else measure_ios_one(path, args)
        )
        results.append(result)
        if args.clock_only:
            clock = result.get("clock") or {}
            print(
                f"\n### {result['source_name']}  clock-only  crop={tuple(result['crop'])}"
                f"  scale={result['px_scale']:g}  polarity={result['polarity']}"
            )
            print(
                "CLOCK         cap height=%.2f px  digit heights=%s  top=%.1f bottom=%.1f"
                % (
                    clock.get("cap_height_px") or 0.0,
                    ["%.2f" % v for v in clock.get("digit_heights_px", [])],
                    clock.get("top_px", 0.0), clock.get("bottom_px", 0.0),
                )
            )
            if result.get("ratios"):
                for key, value in result["ratios"].items():
                    print("              %-34s %s" % (key, _fmt(value, 3)))
        else:
            _print_ios_report(result, args.dump)
        if args.annotate and len(args.images) == 1:
            _annotate(result, args.annotate)
            print(f"\nannotated -> {args.annotate}")

    if args.json:
        payload = results[0] if len(results) == 1 else {"sources": results}
        with open(args.json, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, indent=2)
        print(f"\nJSON -> {args.json}")
    return 0


def main() -> int:
    if len(sys.argv) > 1 and sys.argv[1] == "ios":
        return ios_main(sys.argv[2:])
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
