"""Route every module log call through `L` instead of `android.util.Log`.

Why this exists: OxygenOS's SystemUI drops `android.util.Log` output - the lines never reach logcat,
while `L` also writes through `XposedBridge.log`, which LSPosed captures in
`/data/adb/lspd/log/modules_<boot>.log`. A module that logs only through `android.util.Log` is therefore
undiagnosable on this ROM (it cost a full verification round: a working module looked completely dead).

This rewrites `Log.x(TAG, ...)` -> `L.x(...)`, drops the now-unused `android.util.Log` import, and adds
`import io.github.kvmy666.duostatusbar.L` in sorted position where the file is not in the root package.
It is idempotent, so it is safe to run again after adding code.

Usage: python tools/route-module-logs.py [--check]

    --check   report what would change and exit non-zero if anything would (for CI/lint use)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "app" / "src" / "main" / "java"
LOGGER = SOURCES / "io" / "github" / "kvmy666" / "duostatusbar" / "L.kt"
L_IMPORT = "import io.github.kvmy666.duostatusbar.L"
ROOT_PACKAGE = "io.github.kvmy666.duostatusbar"

CALL = re.compile(r"\bLog\.([iwdev])\(\s*TAG,\s*")
IMPORT = re.compile(r"^import android\.util\.Log\r?\n", re.MULTILINE)
PACKAGE = re.compile(r"^package\s+(\S+)", re.MULTILINE)
# Any remaining android.util.Log use: calls outside the TAG shape, or other Log helpers.
LEFT = re.compile(r"\bLog\.\w")
# A file only wants the L import if it actually logs through L.
USES_L = re.compile(r"\bL\.(i|w|d|v|e)\(")


def add_import(text: str, line: str) -> str:
    lines = text.splitlines(keepends=True)
    block = [i for i, l in enumerate(lines) if l.startswith("import ")]
    if not block:
        pkg = next(i for i, l in enumerate(lines) if l.startswith("package "))
        lines.insert(pkg + 1, "\n" + line + "\n")
        return "".join(lines)
    at = block[-1] + 1
    for i in block:
        if lines[i].strip() > line:
            at = i
            break
    lines.insert(at, line + "\n")
    return "".join(lines)


def convert(path: Path) -> bool:
    original = path.read_text(encoding="utf-8")
    text = CALL.sub(lambda m: f"L.{m.group(1)}(", original)

    # The import only goes when nothing in the file still wants android.util.Log.
    if not LEFT.search(text.replace("L.", "")):
        text = IMPORT.sub("", text)

    # `package io.github.kvmy666.duostatusbar` is a *prefix* of the hook package too, so the package
    # comparison has to be exact - a substring test silently skipped every `...hook.*` file.
    pkg = PACKAGE.search(text)
    in_root = bool(pkg) and pkg.group(1) == ROOT_PACKAGE
    if USES_L.search(text):
        if not in_root and L_IMPORT not in text:
            text = add_import(text, L_IMPORT)
    elif L_IMPORT in text:
        # Never leave an unused import behind: this script has to be safe to re-run.
        text = text.replace(L_IMPORT + "\n", "")

    if text == original:
        return False
    path.write_text(text, encoding="utf-8")
    return True


def main() -> int:
    check = "--check" in sys.argv
    changed: list[str] = []
    for path in sorted(SOURCES.rglob("*.kt")):
        if path == LOGGER or "reverse" in path.parts:
            continue
        if check:
            before = path.read_text(encoding="utf-8")
            pkg = PACKAGE.search(before)
            in_root = bool(pkg) and pkg.group(1) == ROOT_PACKAGE
            uses_l = bool(USES_L.search(before))
            stale = L_IMPORT in before and not uses_l
            missing = uses_l and not in_root and L_IMPORT not in before
            if CALL.search(before) or missing or stale:
                changed.append(str(path.relative_to(ROOT)))
            continue
        if convert(path):
            changed.append(str(path.relative_to(ROOT)))
    for c in changed:
        print(("would rewrite " if check else "rewrote ") + c)

    # Leftovers are calls the pattern does not cover and need a human eye.
    leftovers = []
    for path in sorted(SOURCES.rglob("*.kt")):
        if path == LOGGER or "reverse" in path.parts:
            continue
        for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if LEFT.search(line):
                leftovers.append(f"{path.relative_to(ROOT)}:{n}: {line.strip()}")
    print(f"\n{len(changed)} file(s) {'to fix' if check else 'fixed'}, "
          f"{len(leftovers)} log call(s) left for review")
    for l in leftovers:
        print("  " + l)
    return 1 if (check and changed) else 0


if __name__ == "__main__":
    raise SystemExit(main())
