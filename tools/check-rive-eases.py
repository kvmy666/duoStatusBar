"""Fail if a Rive keyframe asks for an ease it does not have.

`rive --verify` builds this clean and `rive inspect` reports nothing, but per
`rive docs gotchas`:

    `cubic` says "use the curve nested inside me" - with no CubicEaseInterpolator
    child there is no curve, and the segment does not ease. Same for `elastic`
    without an ElasticInterpolator.

So an animation can be *entirely* linear while every keyframe says `cubic`, which
is exactly what this project shipped: 27 cubic keyframes, zero interpolators.

In RML a keyframe with a child is written as an open element, and one without is
self-closing. That is almost the whole test - with one exception: the ease on the
**last** keyframe of a property is never read, because there is no segment after
it (`rive docs easing`). A terminal keyframe is allowed to be inert; any other
one is a bug.

Usage:
    python tools/check-rive-eases.py [scene.rml]      # exit 1 if any ease is inert
"""

import re
import sys

EASING = {"cubic": "CubicEaseInterpolator", "cubicValue": "CubicValueInterpolator", "elastic": "ElasticInterpolator"}
DEFAULT = "riva/duo/scene.rml"


def main() -> None:
    path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT
    text = open(path, encoding="utf-8").read()

    # Group keyframes by the KeyedProperty that owns them: the last one in each group is terminal.
    property_pattern = re.compile(r"<KeyedProperty\b.*?</KeyedProperty>", re.S)
    keyframe_pattern = re.compile(r"<KeyFrame(\w+)([^>]*?)(/?)>", re.S)
    inert = []
    eased = 0

    for prop in property_pattern.finditer(text):
        block = prop.group(0)
        frames = list(keyframe_pattern.finditer(block))
        for index, match in enumerate(frames):
            kind, attrs, self_closing = match.group(1), match.group(2), match.group(3)
            interp = re.search(r'interpolationType="(\w+)"', attrs)
            if not interp:
                continue  # `hold` is the default and needs no curve
            name = interp.group(1)
            if name not in EASING:
                continue
            if not self_closing:
                eased += 1
            elif index < len(frames) - 1:
                line = text.count("\n", 0, prop.start() + match.start()) + 1
                inert.append((line, name, kind))
            else:
                eased += 1  # terminal: its curve would never be read

    for line, name, kind in inert:
        print(f"{path}:{line}: {name} keyframe ({kind}) has no {EASING[name]} child - it does not ease")

    total = len(inert) + eased
    print(f"{total} easing keyframe(s): {eased} carry a curve, {len(inert)} are inert")
    if inert:
        sys.exit(1)


if __name__ == "__main__":
    main()
