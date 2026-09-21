"""Dump which LSPosed modules are enabled and which processes they are scoped to.

Usage: python tools/dump-lsposed-scope.py <path-to-modules_config.db>

The scope database lives at /data/adb/lspd/config/modules_config.db on the device
(pull the db plus its -wal/-shm so no commits are lost).
"""
import sqlite3
import sys

def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    con = sqlite3.connect(sys.argv[1])

    states = {(m, u): e for (m, u, e, _b) in con.execute(
        "select module_pkg_name, user_id, enabled, scope_request_blocked from modules_state")}
    scopes: dict[str, list[str]] = {}
    for (m, app, _u) in con.execute(
            "select module_pkg_name, app_pkg_name, user_id from scope"):
        scopes.setdefault(m, []).append(app)

    print("\n=== modules ===")
    for (pkg,) in con.execute("select module_pkg_name from modules order by module_pkg_name"):
        enabled = any(v for (m, _u), v in states.items() if m == pkg)
        apps = sorted(scopes.get(pkg, []))
        print(f"\n{'ON ' if enabled else 'off'} {pkg}   ({len(apps)} scoped)")
        for a in apps:
            star = " <== needed" if (a == "com.android.systemui" and "duostatusbar" in pkg) else ""
            print(f"       {a}{star}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
