"""Route every module log call through `L` instead of `android.util.Log`.

Why this exists: OxygenOS's SystemUI drops `android.util.Log` output - the lines never reach logcat,
while `L` also writes through `XposedBridge.log`, which LSPosed captures in
`/data/adb/lspd/log/modules_<boot>.log`. A module that logs only through `android.util.Log` is therefore
undiagnosable on this ROM (it cost a full verification round: a working module looked completely dead).

This rewrites `Log.x(TAG, ...)` -> `L.x(...)`, drops the now-unused `android.util.Log` import, and adds
`import io.github.kvmy666.duostatusbar.L` in sorted position where the file is not in the root package.
It is idempotent, so it is safe to run again after adding code.

Usage: python tools/route-module-logs.py [--check]

    --check   report what would change and fail if a file would change or if an unmatched
              `Log.x` call is left in code; comments and string/char literals are ignored
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
USES_L = re.compile(r"\bL\.\w+\(")


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


def code_only(text: str) -> str:
    """Blank comments and string/char literals, keeping every newline and column count.

    Kotlin block comments nest, raw strings use `\"\"\"`, and ordinary strings/chars honour a
    backslash escape. Replacing the content with spaces means `Log.x` mentioned in a KDoc or a
    string (a help message, a probe payload) is not reported as a live call.
    """
    out = list(text)
    i, n, depth = 0, len(text), 0
    mode = "code"

    def blank(j: int) -> None:
        if text[j] != "\n":
            out[j] = " "

    while i < n:
        c = text[i]
        if mode == "code":
            if text.startswith("//", i):
                out[i] = out[i + 1] = " "
                mode, i = "line", i + 2
            elif text.startswith("/*", i):
                out[i] = out[i + 1] = " "
                depth, mode, i = 1, "block", i + 2
            elif text.startswith('"""', i):
                out[i] = out[i + 1] = out[i + 2] = " "
                mode, i = "raw", i + 3
            elif c == '"':
                blank(i)
                mode, i = "string", i + 1
            elif c == "'":
                blank(i)
                mode, i = "char", i + 1
            else:
                i += 1
        elif mode == "line":
            if c == "\n":
                mode = "code"
            else:
                blank(i)
            i += 1
        elif mode == "block":
            if text.startswith("/*", i):
                out[i] = out[i + 1] = " "
                depth, i = depth + 1, i + 2
            elif text.startswith("*/", i):
                out[i] = out[i + 1] = " "
                depth, i = depth - 1, i + 2
                if depth == 0:
                    mode = "code"
            else:
                blank(i)
                i += 1
        elif mode in ("string", "char"):
            quote = '"' if mode == "string" else "'"
            if c == "\\":
                blank(i)
                if i + 1 < n:
                    blank(i + 1)
                    i += 2
                else:
                    i += 1
            elif c == quote:
                blank(i)
                mode, i = "code", i + 1
            else:
                blank(i)
                i += 1
        else:  # raw
            if text.startswith('"""', i):
                out[i] = out[i + 1] = out[i + 2] = " "
                mode, i = "code", i + 3
            else:
                blank(i)
                i += 1
    return "".join(out)


def convert(path: Path) -> bool:
    original = path.read_text(encoding="utf-8")
    # Rewrite only real code. Find calls in the masked text so a call-shaped comment or string is
    # never matched, then re-match each start against the original: the masked span's trailing `\s*`
    # runs into the blanked string body, and using it would eat the `"x"` argument.
    masked = code_only(original)
    spans = []
    for m in CALL.finditer(masked):
        real = CALL.match(original, m.start())
        if real:
            spans.append(real)
    text = original
    for m in reversed(spans):
        start, end = m.span()
        text = text[:start] + f"L.{m.group(1)}(" + text[end:]

    # Every import decision comes from code only: a comment or string must not keep a call alive.
    after_masked = code_only(text)
    if not LEFT.search(after_masked):
        text = IMPORT.sub("", text)

    # `package io.github.kvmy666.duostatusbar` is a *prefix* of the hook package too, so the package
    # comparison has to be exact - a substring test silently skipped every `...hook.*` file.
    pkg = PACKAGE.search(text)
    in_root = bool(pkg) and pkg.group(1) == ROOT_PACKAGE
    if USES_L.search(after_masked):
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
            masked = code_only(before)
            pkg = PACKAGE.search(before)
            in_root = bool(pkg) and pkg.group(1) == ROOT_PACKAGE
            uses_l = bool(USES_L.search(masked))
            stale = L_IMPORT in before and not uses_l
            missing = uses_l and not in_root and L_IMPORT not in before
            if CALL.search(masked) or missing or stale:
                changed.append(str(path.relative_to(ROOT)))
            continue
        if convert(path):
            changed.append(str(path.relative_to(ROOT)))
    for c in changed:
        print(("would rewrite " if check else "rewrote ") + c)

    # Leftovers are calls the pattern does not cover and need a human eye. Scan code only: a KDoc
    # or string literal that merely mentions Log.x is not a live call and must not fail the gate.
    leftovers = []
    for path in sorted(SOURCES.rglob("*.kt")):
        if path == LOGGER or "reverse" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        raw_lines = text.splitlines()
        for n, line in enumerate(code_only(text).splitlines(), 1):
            if LEFT.search(line):
                leftovers.append(f"{path.relative_to(ROOT)}:{n}: {raw_lines[n - 1].strip()}")
    print(f"\n{len(changed)} file(s) {'to fix' if check else 'fixed'}, "
          f"{len(leftovers)} log call(s) left for review")
    if leftovers:
        print(f"error: {len(leftovers)} log call(s) the router cannot rewrite")
        for l in leftovers:
            print("  " + l)
        print("hint: route these through L: module logs must reach logcat + Xposed")
    return 1 if (check and (changed or leftovers)) else 0


if __name__ == "__main__":
    raise SystemExit(main())
