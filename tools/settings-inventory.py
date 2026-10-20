#!/usr/bin/env python3
"""Row/key inventory of the settings screens at a git revision.

Usage: settings-inventory.py REV [--json]

Reads every settings screen (classes extending BaseSettingsActivity plus the helpers they
share) straight from git, and prints
  * the user-facing row titles, per screen (literal first argument of settingsRow(...)), and
  * the SharedPreferences keys the screens reference (get*/put*/remove/contains with a literal
    key, and *_KEY / KEY_* / PREF_* constants).
Used to prove a settings reorganisation moved rows without adding, removing or renaming a
setting: run it on both revisions and diff the results.
"""
import json, re, subprocess, sys

BASE = "app/src/main/kotlin/com/trevornk/ramblr/"
ROW = re.compile(r'\bsettingsRow\(\s*(?:title\s*=\s*)?(?:"((?:[^"\\]|\\.)*)"|([^\n,]+))', re.S)
KEYCALL = re.compile(r'\.(?:get(?:String|Boolean|Int|Long|Float|StringSet)|put(?:String|Boolean|Int|Long|Float|StringSet)|remove|contains)\(\s*"([A-Za-z0-9_.]+)"')
KEYCONST = re.compile(r'\b((?:[A-Z][A-Z0-9_]*_KEY|KEY_[A-Z0-9_]+|PREF_[A-Z0-9_]+))\b')

# Every identifier-like string literal in the app's Kotlin: a superset check for keys that reach the
# screens through helper objects instead of a literal get*/put* call.
SNAKE = re.compile(r'"([A-Za-z][A-Za-z0-9_.]{2,60})"')

def git(*a):
    return subprocess.run(["git", *a], check=True, capture_output=True, text=True).stdout

STRING = re.compile(r'<string name="([^"]+)"[^>]*>(.*?)</string>', re.S)
RES = re.compile(r'getString\(R\.string\.(\w+)\)')

def inventory(rev):
    res = dict(STRING.findall(git("show", f"{rev}:app/src/main/res/values/strings.xml")))
    files = [f for f in git("ls-tree", "-r", "--name-only", rev, "--", BASE).split("\n") if f.endswith(".kt")]
    screens = {}
    keys = set()
    literals = set()
    for f in files:
        src = git("show", f"{rev}:{f}")
        literals.update(SNAKE.findall(src))
        if "BaseSettingsActivity" not in src and "settingsRow(" not in src:
            continue
        name = f.rsplit("/", 1)[1][:-3]
        rows = []
        for m in ROW.finditer(src):
            if m.group(1) is not None:
                rows.append(m.group(1))
                continue
            ref = RES.fullmatch(m.group(2).strip())
            if ref and ref.group(1) in res:
                rows.append(res[ref.group(1)].replace("\\'", "'"))
            else:
                rows.append("<dynamic:" + m.group(2).strip() + ">")
        if rows:
            screens[name] = rows
        keys.update(KEYCALL.findall(src))
        keys.update(KEYCONST.findall(src))
    return screens, sorted(keys), sorted(literals)

if __name__ == "__main__":
    screens, keys, literals = inventory(sys.argv[1])
    if "--json" in sys.argv:
        print(json.dumps({"screens": screens, "keys": keys, "literals": literals}, indent=1, sort_keys=True))
    else:
        for s, rows in sorted(screens.items()):
            print(f"## {s}")
            for r in rows:
                print("  ", r)
        print("## keys", len(keys))
        print("\n".join(keys))
        print("## identifier-like literals", len(literals))
