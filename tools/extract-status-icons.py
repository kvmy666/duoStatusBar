#!/usr/bin/env python3
"""Inventory and copy the status-bar icon resources from decoded vendor SystemUI trees.

The decoded APKs live under `reverse/` (gitignored). This tool copies only drawable candidates into
`reverse/icon-packs/<ui>/<category>/` and writes `manifest.json`; it never copies a whole APK and never
touches `app/src/main/assets`.

Usage:
    python3 tools/extract-status-icons.py
    python3 tools/extract-status-icons.py --root reverse/decoded --out reverse/icon-packs
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
from pathlib import Path

UI_LABELS = {
    "oneplus-phone": "OnePlus CPH2747 (Android 16, pulled)",
    "oneplus": "OnePlus System UI 16.00.14-2 (corpus)",
    "samsung": "Samsung System UI 16-11",
    "xiaomi": "Xiaomi System UI 17-03",
    "vivo": "vivo OriginOS 16.0.7.194",
    "motorola": "Motorola System UI 15.7",
    "nothing": "Nothing System UI 15.0.176",
    "oppo": "OPPO System UI 3.0.4 (old)",
    "honor": "Honor System UI 11.0.0.1 (old)",
}

DRAWABLE_DIR = re.compile(r"^res/(drawable|mipmap|raw)(-|/|$)")
RASTER = {".png", ".webp", ".jpg", ".jpeg", ".gif"}
VECTOR = {".xml"}


def category(name: str) -> str | None:
    n = name.lower()
    if any(x in n for x in ("layout", "view", "row", "slider", "header", "dialog", "button")):
        return None
    if "wifi" in n and ("signal" in n or "stat_" in n):
        return "wifi"
    if ("signal" in n or "stat_sys_signal" in n) and "wifi" not in n and "vowifi" not in n:
        return "signal"
    if "battery" in n and not any(x in n for x in ("saver", "percentage", "progress", "chip")):
        return "battery"
    if "airplane" in n:
        return "airplane"
    if "torch" in n or "flashlight" in n:
        return "flashlight"
    if "dnd" in n or "do_not_disturb" in n:
        return "dnd"
    if "alarm" in n and "clock" not in n:
        return "alarm"
    if "volume" in n and "ambient" not in n:
        return "volume"
    if "location" in n:
        return "location"
    if "mic" in n:
        return "mic"
    if "screenshot" in n:
        return "screenshot"
    return None


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default="reverse/decoded")
    parser.add_argument("--out", default="reverse/icon-packs")
    args = parser.parse_args()
    root = Path(args.root)
    out = Path(args.out)
    if not root.is_dir():
        print(f"ERROR: decoded root missing: {root}")
        return 2
    out.mkdir(parents=True, exist_ok=True)
    manifest = {"source_root": str(root), "generated_from": "apktool d -s --no-src", "icons": []}
    totals: dict[str, dict[str, int]] = {}
    for ui_dir in sorted(p for p in root.iterdir() if p.is_dir()):
        ui = ui_dir.name
        counts: dict[str, int] = {}
        for path in sorted(ui_dir.glob("res/**/*")):
            if not path.is_file():
                continue
            rel = path.relative_to(ui_dir).as_posix()
            if not DRAWABLE_DIR.match(rel):
                continue
            ext = path.suffix.lower()
            if ext not in RASTER | VECTOR:
                continue
            cat = category(path.name)
            if cat is None:
                continue
            target = out / ui / cat / path.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
            manifest["icons"].append({
                "ui": ui,
                "label": UI_LABELS.get(ui, ui),
                "category": cat,
                "file": target.relative_to(out).as_posix(),
                "source": f"{ui}/{rel}",
                "bytes": target.stat().st_size,
                "sha256": digest(target),
                "kind": "vector" if ext in VECTOR else "raster",
            })
            counts[cat] = counts.get(cat, 0) + 1
        totals[ui] = counts
        print(f"{ui:16} " + " ".join(f"{k}={v}" for k, v in sorted(counts.items())))
    (out / "manifest.json").write_text(
        json.dumps({**manifest, "counts": totals}, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(f"icons={len(manifest['icons'])} manifest={out / 'manifest.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
