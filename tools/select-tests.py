#!/usr/bin/env python3
"""Fail-safe diff-based unit-test selector for Duo Status Bar pull requests.

The map is rebuilt from the working tree on every run; no committed JSON can go
stale.  A changed path is only narrowed when the selector can prove what it
touches.  Anything unproven -- an unknown path, a deleted/renamed/type-changed
file, a global
build input, a source file missing from the map, or any parse/read/git error --
prints ``FULL`` so the whole unit suite runs.

Two deliberate safety choices are documented here because they look surprising:

* An empty changed list is ``FULL``.  A normal PR always has a diff, so an empty
  list means the caller failed to collect one; doing nothing would hide that.
* More than 80% of test classes selected is ``FULL``.  At that point the Gradle
  filter saves little, and one missed edge would cost a release.  Class count is
  used (not timing) because it is deterministic and needs no stored measurements.
* A changed main file with no dependent test is ``FULL``.  Running only the
  always-run set would silently report a pass for code nothing exercised.

stdout carries only the decision (``FULL`` or ``--tests=...`` lines, or one JSON
object).  Diagnostics go to stderr.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from collections import defaultdict

REPO_PREFIX = "io.github.kvmy666.duostatusbar"
MAIN_ROOT = "app/src/main/java"
TEST_ROOT = "app/src/test/java"

# Cross-cutting suites that read repo files or bytecode; they must run even when
# the diff only touches docs.  Missing one of these is itself abnormal -> FULL.
ALWAYS_TESTS = (
    "IosGeometryParityTest",
    "RivePropertyParityTest",
    "CellReaderMappingTest",
    "IosRiveAbsenceTest",
    "IosBinderTest",
)

CLASS_THRESHOLD = 0.80

# Global inputs: any change here invalidates the whole dependency map.
FULL_EXACT = frozenset({
    "build.gradle.kts",
    "settings.gradle.kts",
    "app/build.gradle.kts",
    "gradle.properties",
    "app/src/main/AndroidManifest.xml",
    "app/proguard-rules.pro",
})
FULL_PREFIXES = (
    "gradle/",
    "app/src/main/res/",
    "app/src/main/assets/",
    "app/src/main/aidl/",
    "tools/",
    ".github/workflows/",
    "rive/",
    "app/src/test/resources/",
    "app/src/androidTest/",
)

# Non-code paths that cannot affect compiled behaviour.
SAFE_EXACT = frozenset({
    "README.md",
    "SUMMARY",
    "RELEASE_NOTES.md",
    "LICENSE",
    ".gitignore",
    ".gitattributes",
})
SAFE_PREFIXES = (
    "docs/",
    "agent/",
    "weapons/",
    "relay/",
)

PKG_RE = re.compile(r"^\s*package\s+([\w.]+)", re.M)
IMPORT_RE = re.compile(
    r"^import\s+([\w.]+(?:\.\*)?)(?:\s+as\s+(\w+))?\s*$", re.M
)
IDENT_RE = re.compile(r"\b[A-Za-z_]\w*\b")
DECL_RE = re.compile(
    r"^(?P<indent>\s*)(?:(?:public|internal|private|protected|open|abstract|sealed|data|value|enum|"
    r"annotation|inline|suspend|operator|infix|tailrec|external|expect|actual|const|lateinit|override|"
    r"companion|fun)\s+)*(?P<kind>class|interface|object|typealias|fun|val|var)\s+"
    r"(?:<[^>{]*>\s*)?(?:[\w.<>?]+\.)?(?P<name>[A-Za-z_]\w*)"
)
FQN_REF_RE = re.compile(re.escape(REPO_PREFIX) + r"(?:\.\w+)+")
PACKAGE_IMPORT_RE = re.compile(r"(?m)^\s*(?:package|import)\b[^\n]*")
BUILTINS = set("""Any Array Boolean Byte Char CharSequence Double Float Int Long Map MutableMap
    MutableList MutableSet Nothing Number Pair Set String Triple Unit List Collection Iterable
    Sequence Comparable Throwable Exception RuntimeException Object""".split())


# ---------------------------------------------------------------------------
# Source scanning


def _read_text(path):
    with open(path, "r", encoding="utf-8-sig") as handle:
        return handle.read()


def _files_under(root, rel_root, suffix):
    base = os.path.join(root, rel_root)
    if not os.path.isdir(base):
        raise RuntimeError("missing source directory: %s" % rel_root)
    found = []
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames.sort()
        for name in sorted(filenames):
            if name.endswith(suffix):
                rel = os.path.relpath(os.path.join(dirpath, name), root)
                found.append(rel.replace(os.sep, "/"))
    return sorted(found)


def _strip_comments(text):
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    text = re.sub(r"//[^\n]*", " ", text)
    return text


def _strip_literals(text):
    """Remove string/char literals so words in messages do not look like deps."""
    text = re.sub(r'"""(?:.|\n)*?"""', '""', text)
    text = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', text)
    text = re.sub(r"'(?:\\.|[^'\\\n])*'", "''", text)
    return text


def _code(text):
    return _strip_literals(_strip_comments(text))


def _qualified_refs(code):
    """Fully-qualified repo references used in code, without an import.

    Package and import clauses are removed first: their own ``io.github...``
    prefix names a package, not a single file, and resolving it would select
    every sibling.
    """
    return set(FQN_REF_RE.findall(PACKAGE_IMPORT_RE.sub(" ", code)))


class Index:
    """Repository dependency index, rebuilt per run."""

    def __init__(self):
        self.main_files = []
        self.main_set = set()
        self.package_of = {}
        self.main_direct = {}
        self.main_closure = {}
        self.tests_by_main = defaultdict(set)
        self.test_files = []
        self.test_set = set()
        self.test_fqn = {}
        self.test_fqn_by_simple = defaultdict(list)
        self.all_test_fqns = []


def build_index(root):
    index = Index()

    index.main_files = _files_under(root, MAIN_ROOT, ".kt")
    if not index.main_files:
        raise RuntimeError("no Kotlin sources under %s" % MAIN_ROOT)
    index.main_set = set(index.main_files)

    type_name_files = defaultdict(set)
    # Top-level values/functions keyed by (package, name).  Kotlin resolves
    # these without an import only inside their own package, and upper-case
    # consts (TAG, REVEAL_MS) collide across unrelated packages, so the package
    # is part of the key instead of a global name map.
    symbols_by_pkg = defaultdict(set)
    package_files = defaultdict(set)
    fqn_to_file = {}
    code_by_file = {}

    for rel in index.main_files:
        text = _read_text(os.path.join(root, rel))
        pkg_match = PKG_RE.search(text)
        pkg = pkg_match.group(1) if pkg_match else ""
        index.package_of[rel] = pkg
        package_files[pkg].add(rel)
        code = _code(text)
        code_by_file[rel] = code

        for line in text.splitlines():
            match = DECL_RE.match(line)
            if not match:
                continue
            kind = match.group("kind")
            name = match.group("name")
            indent = match.group("indent")
            if kind in ("fun", "val", "var") and indent:
                continue
            if name in ("Companion", "constructor"):
                continue
            if kind in ("class", "interface", "object", "typealias"):
                type_name_files[name].add(rel)
            elif not indent:
                # Any top-level value or function, lower- or upper-case.  The
                # package in the key keeps same-named consts apart.
                symbols_by_pkg[(pkg, name)].add(rel)
            fqn_to_file["%s.%s" % (pkg, name)] = rel

        stem = os.path.splitext(os.path.basename(rel))[0]
        fqn_to_file.setdefault("%s.%s" % (pkg, stem), rel)

    def resolve_import(fqn):
        if fqn.endswith(".*"):
            return sorted(package_files.get(fqn[:-2], ()))
        if fqn in fqn_to_file:
            return [fqn_to_file[fqn]]
        parts = fqn.split(".")
        for cut in range(len(parts) - 1, 0, -1):
            prefix = ".".join(parts[:cut])
            if prefix in fqn_to_file:
                return [fqn_to_file[prefix]]
        return []

    def direct_deps(rel):
        code = code_by_file[rel]
        pkg = index.package_of[rel]
        deps = set()
        for match in IMPORT_RE.finditer(code):
            fqn = match.group(1)
            if fqn.startswith(REPO_PREFIX):
                deps.update(resolve_import(fqn))
        for fqn in _qualified_refs(code):
            deps.update(resolve_import(fqn))
        for ident in set(IDENT_RE.findall(code)):
            if ident in BUILTINS:
                continue
            files = set(type_name_files.get(ident, ()))
            files.update(symbols_by_pkg.get((pkg, ident), ()))
            for candidate in files:
                if index.package_of.get(candidate) == pkg:
                    deps.add(candidate)
        deps.discard(rel)
        return deps

    index.main_direct = {rel: direct_deps(rel) for rel in index.main_files}
    for rel in index.main_files:
        seen = set()
        stack = list(index.main_direct[rel])
        while stack:
            current = stack.pop()
            if current in seen:
                continue
            seen.add(current)
            for nxt in index.main_direct.get(current, ()):
                if nxt not in seen:
                    stack.append(nxt)
        seen.discard(rel)
        index.main_closure[rel] = seen

    index.test_files = _files_under(root, TEST_ROOT, "Test.kt")
    index.test_set = set(index.test_files)

    for rel in index.test_files:
        text = _read_text(os.path.join(root, rel))
        pkg_match = PKG_RE.search(text)
        pkg = pkg_match.group(1) if pkg_match else ""
        code = _code(text)
        stem = os.path.splitext(os.path.basename(rel))[0]
        fqn = "%s.%s" % (pkg, stem) if pkg else stem
        index.test_fqn[rel] = fqn
        index.test_fqn_by_simple[stem].append(fqn)

        imports = []
        aliases = {}
        for match in IMPORT_RE.finditer(code):
            name, alias = match.group(1), match.group(2)
            if not name.startswith(REPO_PREFIX):
                continue
            imports.append(name)
            if alias and not name.endswith(".*"):
                aliases[alias] = name

        direct = set()
        for name in imports:
            direct.update(resolve_import(name))
        for alias, name in aliases.items():
            if re.search(r"\b%s\b" % re.escape(alias), code):
                direct.update(resolve_import(name))
        for name in _qualified_refs(code):
            direct.update(resolve_import(name))
        for ident in set(IDENT_RE.findall(code)):
            if ident in aliases or ident in BUILTINS:
                continue
            files = set(type_name_files.get(ident, ()))
            files.update(symbols_by_pkg.get((pkg, ident), ()))
            for candidate in files:
                # A cross-package Kotlin reference must be imported; the
                # import edge above covers it.
                if index.package_of.get(candidate) == pkg:
                    direct.add(candidate)

        transitive = set(direct)
        for dep in direct:
            transitive.update(index.main_closure.get(dep, ()))
        for dep in transitive:
            index.tests_by_main[dep].add(rel)

    for values in index.test_fqn_by_simple.values():
        values.sort()
    index.all_test_fqns = sorted(index.test_fqn.values())
    return index


# ---------------------------------------------------------------------------
# Path classification

def _normalize(path):
    path = path.strip().replace("\\", "/")
    while path.startswith("./"):
        path = path[2:]
    return path


def classify_path(path):
    """Return 'main', 'test', 'safe' or 'full' for a normalised relative path."""
    if path in FULL_EXACT:
        return "full"
    if any(path.startswith(prefix) for prefix in FULL_PREFIXES):
        return "full"
    if path.startswith("app/src/test/java/"):
        return "test" if path.endswith("Test.kt") else "full"
    if path.startswith("app/src/main/java/"):
        return "main" if path.endswith(".kt") else "full"
    if path in SAFE_EXACT:
        return "safe"
    if any(path.startswith(prefix) for prefix in SAFE_PREFIXES):
        return "safe"
    return "full"


def resolve_always(index):
    fqns = set()
    missing = []
    for simple in ALWAYS_TESTS:
        matches = index.test_fqn_by_simple.get(simple, ())
        if not matches:
            missing.append(simple)
        else:
            fqns.update(matches)
    return sorted(fqns), missing


# ---------------------------------------------------------------------------
# Selection

def _result(mode, tests, always, reason):
    return {
        "mode": mode,
        "tests": sorted(set(tests)),
        "always": sorted(set(always)),
        "reason": reason,
    }


def select(index, changed_paths, statuses=None, explain=False, log=None):
    statuses = statuses or {}
    log = log or (lambda message: None)

    changed = []
    seen = set()
    for raw in changed_paths:
        path = _normalize(raw)
        if path and path not in seen:
            seen.add(path)
            changed.append(path)
    changed.sort()

    if not changed:
        return _result(
            "full", [], [], "empty changed list; a normal PR always has a diff"
        )

    always, missing = resolve_always(index)
    if missing:
        return _result(
            "full",
            [],
            always,
            "always-run invariant test(s) not found: %s" % ", ".join(sorted(missing)),
        )

    selected = set()
    safe_only = True

    for path in changed:
        status = statuses.get(path)
        if status and status[:1] in ("D", "R", "T"):
            status_code = status[:1]
            status_name = {
                "D": "deleted",
                "R": "renamed",
                "T": "type-changed",
            }[status_code]
            return _result(
                "full",
                [],
                always,
                "%s has diff status %s (%s); deleted, renamed and type-changed"
                " inputs are unsafe" % (path, status_code, status_name),
            )
        kind = classify_path(path)
        if kind == "safe":
            log("safe: %s" % path)
            continue
        safe_only = False
        if kind == "full":
            return _result(
                "full", [], always, "global or unknown input: %s" % path
            )
        if kind == "main":
            if path not in index.main_set:
                return _result(
                    "full",
                    [],
                    always,
                    "main source not present in the dependency map: %s" % path,
                )
            dependents = index.tests_by_main.get(path, set())
            if not dependents:
                return _result(
                    "full",
                    [],
                    always,
                    "no test depends on %s; cannot verify it" % path,
                )
            log("main: %s -> %d dependent test file(s)" % (path, len(dependents)))
            selected.update(dependents)
            continue
        # kind == "test"
        if path not in index.test_set:
            return _result(
                "full",
                [],
                always,
                "test source not present in the dependency map: %s" % path,
            )
        log("test: %s" % path)
        selected.add(path)

    chosen = {index.test_fqn[rel] for rel in selected}
    chosen.update(always)

    total = len(index.all_test_fqns)
    if total and len(chosen) > CLASS_THRESHOLD * total:
        return _result(
            "full",
            [],
            always,
            "selected %d of %d test classes (more than %d%%); running the full suite"
            % (len(chosen), total, int(CLASS_THRESHOLD * 100)),
        )

    if not chosen:
        return _result("none", [], [], "no tests matched the changed paths")

    if safe_only:
        reason = "non-code change(s); running only the always-run invariant set"
    else:
        reason = "%d matched test class(es) plus %d always-run invariant class(es)" % (
            len(chosen) - len(always),
            len(always),
        )

    log("selected: %d/%d class(es); %s" % (len(chosen), total, reason))
    return _result("selected", chosen, always, reason)


def emit(result, fmt):
    if fmt == "json":
        print(
            json.dumps(
                {
                    "mode": result["mode"],
                    "tests": result["tests"],
                    "always": result["always"],
                    "reason": result["reason"],
                }
            )
        )
        return
    if result["mode"] == "full":
        print("FULL")
        return
    for fqn in result["tests"]:
        print("--tests=%s" % fqn)


# ---------------------------------------------------------------------------
# Git / list inputs

def changed_from_git(root, base, head):
    names_cmd = [
        "git",
        "-c",
        "core.quotepath=false",
        "diff",
        "--name-only",
        "--diff-filter=ACMRDT",
        "%s...%s" % (base, head),
    ]
    status_cmd = [
        "git",
        "-c",
        "core.quotepath=false",
        "diff",
        "--name-status",
        "-M",
        "--diff-filter=ACMRDT",
        "%s...%s" % (base, head),
    ]
    try:
        names = subprocess.run(
            names_cmd, cwd=root, capture_output=True, text=True, timeout=30
        )
        statuses_proc = subprocess.run(
            status_cmd, cwd=root, capture_output=True, text=True, timeout=30
        )
    except Exception as exc:  # git missing, timeout, ...
        raise RuntimeError("git diff failed: %s" % exc)
    if names.returncode != 0:
        raise RuntimeError(
            "git diff exited %d: %s"
            % (names.returncode, (names.stderr or "").strip())
        )
    if statuses_proc.returncode != 0:
        raise RuntimeError(
            "git diff --name-status exited %d: %s"
            % (statuses_proc.returncode, (statuses_proc.stderr or "").strip())
        )

    changed = [line.strip() for line in names.stdout.splitlines() if line.strip()]
    statuses = {}
    for line in statuses_proc.stdout.splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        status = parts[0]
        if status[:1] in ("R", "C") and len(parts) >= 3:
            # The new path is what appears in --name-only.
            statuses[parts[2]] = status
        elif len(parts) >= 2:
            statuses[parts[1]] = status
    return changed, statuses


# ---------------------------------------------------------------------------
# Selftest

def _exactly_one(items, what):
    if len(items) != 1:
        raise AssertionError("expected one %s, found %d" % (what, len(items)))
    return items[0]


def _selftest_cases(index, root):
    def always_set():
        fqns, missing = resolve_always(index)
        assert not missing, "always tests missing: %s" % missing
        return set(fqns)

    def main_file(named):
        return _exactly_one(
            [p for p in index.main_files if p.rsplit("/", 1)[-1] == named],
            named,
        )

    def test_fqn(named):
        return _exactly_one(
            list(index.test_fqn_by_simple.get(named, ())), "test " + named
        )

    def test_rel(named):
        return _exactly_one(
            [p for p in index.test_files if p.rsplit("/", 1)[-1] == named],
            named,
        )

    def case_device_facts():
        path = main_file("DeviceFacts.kt")
        result = select(index, [path])
        assert result["mode"] == "selected", result["reason"]
        assert test_fqn("DeviceFactsTest") in result["tests"], result["tests"]

    def case_res_full():
        result = select(index, ["app/src/main/res/values/strings.xml"])
        assert result["mode"] == "full", result["mode"]

    def case_type_change_full():
        path = main_file("DeviceFacts.kt")
        result = select(index, [path], statuses={path: "T"})
        assert result["mode"] == "full", result["mode"]
        assert "T" in result["reason"], result["reason"]

    def case_unknown_main_full():
        result = select(
            index,
            ["app/src/main/java/io/github/kvmy666/duostatusbar/NewUnknownFile.kt"],
        )
        assert result["mode"] == "full", result["mode"]

    def case_docs_always_only():
        result = select(index, ["docs/ci.md"])
        assert result["mode"] == "selected", result["reason"]
        assert set(result["tests"]) == always_set(), result["tests"]

    def case_bar_tint_only():
        path = test_rel("BarTintTest.kt")
        result = select(index, [path])
        assert result["mode"] == "selected", result["reason"]
        expected = always_set() | {test_fqn("BarTintTest")}
        assert set(result["tests"]) == expected, result["tests"]

    def case_ios_geometry():
        path = main_file("IosGeometry.kt")
        result = select(index, [path])
        assert result["mode"] == "selected", result["reason"]
        tests = result["tests"]
        assert test_fqn("IosGeometryParityTest") in tests, tests
        assert test_fqn("IosGeometryTest") in tests, tests
        assert test_fqn("IosDrawablesTest") in tests, tests
        assert tests == sorted(tests), "output is not deterministic/sorted"
        assert not any(".settings." in fqn for fqn in tests), tests
        assert len(tests) > len(always_set()), tests
        assert select(index, [path])["tests"] == tests, "non-deterministic result"

    def case_tools_full():
        result = select(index, ["tools/select-tests.py"])
        assert result["mode"] == "full", result["mode"]

    def case_empty_full():
        result = select(index, [])
        assert result["mode"] == "full", result["mode"]

    def case_test_helper_full():
        result = select(
            index,
            ["app/src/test/java/io/github/kvmy666/duostatusbar/hook/Helper.kt"],
        )
        assert result["mode"] == "full", result["mode"]

    def case_manifest_full():
        result = select(index, ["app/src/main/AndroidManifest.xml"])
        assert result["mode"] == "full", result["mode"]

    def case_upper_same_package(name, test, symbol):
        path = main_file(name)
        result = select(index, [path])
        assert result["mode"] == "selected", result["reason"]
        assert test_fqn(test) in result["tests"], (symbol, result["tests"])

    def case_home_setup_upper():
        case_upper_same_package(
            "DuoHomeScreen.kt", "HomeSetupGuideTest", "HOME_SETUP_STEP_STRINGS"
        )

    def case_burn_in_upper():
        case_upper_same_package("BurnIn.kt", "BurnInTest", "BURN_IN_HOUR_MS")

    def case_action_download_upper():
        case_upper_same_package(
            "FeatureSet.kt", "MonitorFeatureFilterTest", "ACTION_DOWNLOAD"
        )

    def case_surface_qs_upper():
        case_upper_same_package(
            "FeatureRegistry.kt", "SurfaceFeatureTest", "SURFACE_QS"
        )

    def case_uncovered_main_full():
        path = main_file("DuoApp.kt")
        result = select(index, [path])
        assert result["mode"] == "full", result
        assert "no test depends on" in result["reason"], result["reason"]

    def case_qualified_ref():
        main_path = main_file("StockIconInventory.kt")
        test_path = test_rel("StockIconKeyTest.kt")
        refs = _qualified_refs(_code(_read_text(os.path.join(root, test_path))))
        assert any(r.endswith(".settings.StockIconInventory") for r in refs), (
            "the real fully-qualified reference moved: %s" % sorted(refs)
        )
        result = select(index, [main_path])
        assert result["mode"] == "selected", result["reason"]
        assert test_fqn("StockIconKeyTest") in result["tests"], result["tests"]

    return [
        ("changed DeviceFacts.kt selects DeviceFactsTest", case_device_facts),
        ("changed res/values/strings.xml forces FULL", case_res_full),
        ("type-changed (T) main source forces FULL", case_type_change_full),
        ("unknown main source forces FULL", case_unknown_main_full),
        ("docs-only change runs the always set", case_docs_always_only),
        ("changed BarTintTest selects only that test plus always", case_bar_tint_only),
        ("IosGeometry.kt selects parity, dependents and no settings tests",
         case_ios_geometry),
        ("tools/select-tests.py forces FULL", case_tools_full),
        ("empty changed list forces FULL", case_empty_full),
        ("test Java helper forces FULL", case_test_helper_full),
        ("AndroidManifest.xml forces FULL", case_manifest_full),
        ("DuoHomeScreen.kt selects HomeSetupGuideTest (upper-case const)",
         case_home_setup_upper),
        ("BurnIn.kt selects BurnInTest (upper-case const)", case_burn_in_upper),
        ("FeatureSet.kt selects MonitorFeatureFilterTest (upper-case const)",
         case_action_download_upper),
        ("FeatureRegistry.kt selects SurfaceFeatureTest (upper-case const)",
         case_surface_qs_upper),
        ("unchanged test map for an uncovered main file forces FULL",
         case_uncovered_main_full),
        ("StockIconInventory.kt resolves its fully-qualified test reference",
         case_qualified_ref),
    ]


def run_selftest(root):
    print("select-tests selftest")
    try:
        index = build_index(root)
    except Exception as exc:
        print("FAIL: cannot build dependency map: %s" % exc)
        return 1

    cases = _selftest_cases(index, root)
    passed = 0
    for name, case in cases:
        try:
            case()
        except AssertionError as exc:
            print("FAIL %s: %s" % (name, exc))
        except Exception as exc:  # pragma: no cover - defensive
            print("FAIL %s: unexpected %s: %s" % (name, type(exc).__name__, exc))
        else:
            passed += 1
            print("ok   %s" % name)

    total = len(cases)
    print("SELFTEST PASS %d/%d cases" % (passed, total))
    return 0 if passed == total else 1


# ---------------------------------------------------------------------------
# Entry point

def main(argv=None):
    parser = argparse.ArgumentParser(
        description="Fail-safe diff-based unit-test selector."
    )
    parser.add_argument("--changed-list", help="file with one changed path per line")
    parser.add_argument("--base", help="base commit for git diff")
    parser.add_argument("--head", help="head commit for git diff")
    parser.add_argument(
        "--format",
        choices=("gradle-args", "json"),
        default="gradle-args",
        help="output format (default: gradle-args)",
    )
    parser.add_argument(
        "--explain", action="store_true", help="write selection details to stderr"
    )
    parser.add_argument(
        "--selftest", action="store_true", help="run built-in assertions"
    )
    args = parser.parse_args(argv)

    root = os.path.dirname(os.path.dirname(os.path.realpath(__file__)))

    if args.selftest:
        return run_selftest(root)

    if args.changed_list and (args.base or args.head):
        parser.error("use either --changed-list or --base/--head, not both")
    if bool(args.base) != bool(args.head):
        parser.error("--base and --head must be given together")
    if not args.changed_list and not args.base:
        parser.error("one of --changed-list or --base/--head is required")

    def log(message):
        if args.explain:
            print("select-tests: %s" % message, file=sys.stderr)

    try:
        index = build_index(root)
    except Exception as exc:
        print("select-tests: cannot build dependency map: %s" % exc, file=sys.stderr)
        emit(_result("full", [], [], "cannot build dependency map: %s" % exc), args.format)
        return 0

    if args.changed_list:
        try:
            with open(args.changed_list, "r", encoding="utf-8-sig") as handle:
                changed = handle.read().splitlines()
        except Exception as exc:
            print(
                "select-tests: cannot read changed list: %s" % exc, file=sys.stderr
            )
            emit(
                _result("full", [], [], "cannot read changed list: %s" % exc),
                args.format,
            )
            return 0
        statuses = {}
    else:
        try:
            changed, statuses = changed_from_git(root, args.base, args.head)
        except Exception as exc:
            print("select-tests: %s" % exc, file=sys.stderr)
            emit(_result("full", [], [], str(exc)), args.format)
            return 0

    try:
        result = select(index, changed, statuses, explain=args.explain, log=log)
    except Exception as exc:
        print("select-tests: selection failed: %s" % exc, file=sys.stderr)
        emit(
            _result("full", [], [], "selection failed: %s" % exc),
            args.format,
        )
        return 0

    if args.explain:
        print("select-tests: reason: %s" % result["reason"], file=sys.stderr)
    emit(result, args.format)
    return 0


if __name__ == "__main__":
    sys.exit(main())
