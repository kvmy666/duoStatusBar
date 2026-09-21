"""Render the README GIFs from the real Rive scene.

The project's rule is that the pictures in the docs must be the drawing the module actually
ships, so these are not mock-ups: every frame is a headless render of `rive/duo/scene.rml`
with the same view-model values the status bar sends.

Usage (from the repository root):

    python tools/make-readme-gifs.py

Needs the Rive CLI on PATH or at %USERPROFILE%\\.rive\\bin\\rive.exe, and Pillow.
"""

import os
import subprocess
import sys
import tempfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT = os.path.join(ROOT, "rive", "duo")
OUT_DIR = os.path.join(ROOT, "docs", "media")
RIVE = os.environ.get("RIVE_CLI") or os.path.join(
    os.path.expanduser("~"), ".rive", "bin", "rive.exe"
)

VIEWPORT = "120x120"
SCALE = 2  # the GIF is shown at 2x, so it stays crisp on a HiDPI screen
FRAME_MS = 60
HOLD = 5  # how many times a settled frame is repeated


def render(advance, data):
    """One headless frame; returns a PIL image."""
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, "frame.png")
        cmd = [
            RIVE, ".", f"--screenshot={out}", f"--viewport={VIEWPORT}",
            f"--advance={advance}",
        ]
        for item in data:
            cmd.append(f"--data={item}")
        subprocess.run(
            cmd, cwd=PROJECT, check=True,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        image = Image.open(out).convert("RGB")
        return image.resize((image.width * SCALE, image.height * SCALE), Image.LANCZOS)


def save_gif(name, frames):
    if not frames:
        raise SystemExit(f"{name}: no frames")
    path = os.path.join(OUT_DIR, name)
    # Re-encode to a shared palette with no transparency and no disposal tricks. PIL's default
    # `optimize=True` merges identical frames and can leave delta frames some decoders render badly;
    # this form is what every browser (and GitHub's image proxy) renders the same way.
    palette = frames[0].convert("P", palette=Image.ADAPTIVE, colors=128)
    quantized = [frame.quantize(palette=palette, dither=Image.NONE) for frame in frames]
    quantized[0].save(
        path, save_all=True, append_images=quantized[1:],
        duration=FRAME_MS, loop=0, optimize=False, disposal=1,
    )
    print(f"wrote {os.path.relpath(path, ROOT)} ({len(frames)} frames)")


def stepped(start, stop, step):
    value = start
    while value <= stop:
        yield value
        value += step


def arrival():
    frames = [render(n, ["revealMs=1000"]) for n in stepped(0, 78, 4)]
    save_gif("arrival.gif", frames)


def charging():
    data = ["charging=true", "animateCharge=true"]
    frames = [render(n, data) for n in stepped(0, 96, 5)]
    save_gif("charging.gif", frames)


def modes():
    """Wi-Fi -> airplane -> DND -> 5G, morphing through each hand-over."""
    frames = []
    # Settled Wi-Fi.
    frames += [render(60, ["middleMode=1"])] * HOLD
    # Wi-Fi -> airplane: the arcs retract, the plane grows.
    frames += [render(n, ["middleMode=2"]) for n in stepped(0, 28, 3)]
    frames += [render(60, ["middleMode=2"])] * HOLD
    # Airplane -> DND.
    frames += [render(n, ["middleMode=3"]) for n in stepped(0, 32, 3)]
    frames += [render(60, ["middleMode=3"])] * HOLD
    # DND -> the cellular generation.
    frames += [render(n, ["middleMode=4", "networkText=5G"]) for n in stepped(0, 28, 3)]
    frames += [render(60, ["middleMode=4", "networkText=5G"])] * HOLD
    # Back to Wi-Fi.
    frames += [render(n, ["middleMode=1"]) for n in stepped(0, 24, 3)]
    frames += [render(60, ["middleMode=1"])] * HOLD
    save_gif("modes.gif", frames)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    if not os.path.exists(RIVE):
        raise SystemExit(f"Rive CLI not found at {RIVE}; set RIVE_CLI")
    arrival()
    charging()
    modes()


if __name__ == "__main__":
    main()
