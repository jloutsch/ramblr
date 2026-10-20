#!/usr/bin/env python3
"""Compare settings-inventory.py output for two revisions.

Usage: compare-settings-inventory.py OLD_REV NEW_REV [--table] [--expect-gone "A,B,..."]
--expect-gone lists launcher rows that were deliberately split into sections or screens of
their own (their leaf rows are still compared). Exit 1 if a preference key / identifier-like literal was added or removed, or if a row title on
OLD_REV's screens no longer appears on any NEW_REV screen. --table prints a markdown
old location -> new location table.
"""
import importlib.util, os, sys
here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("inv", os.path.join(here, "settings-inventory.py"))
inv = importlib.util.module_from_spec(spec); spec.loader.exec_module(inv)

old_rev, new_rev = sys.argv[1], sys.argv[2]
o_screens, o_keys, o_lit = inv.inventory(old_rev)
n_screens, n_keys, n_lit = inv.inventory(new_rev)
bad = False
for label, a, b in (("preference keys", o_keys, n_keys), ("identifier-like literals", o_lit, n_lit)):
    removed, added = sorted(set(a) - set(b)), sorted(set(b) - set(a))
    print(f"{label}: old {len(a)}, new {len(b)}; removed {removed}; added {added}")
    if label == "preference keys" and (removed or added):
        bad = True

def titles(screens, skip=("BaseSettingsActivity",)):
    out = {}
    for s, rows in screens.items():
        if s in skip: continue
        for r in rows:
            if not r.startswith("<dynamic"):
                out.setdefault(r, []).append(s)
    return out
ot, nt = titles(o_screens), titles(n_screens)
gone = set()
if "--expect-gone" in sys.argv:
    gone = set(sys.argv[sys.argv.index("--expect-gone") + 1].split(","))
missing = sorted(set(ot) - set(nt) - gone)
if gone: print(f"launcher rows split on purpose: {sorted(gone)}")
print(f"row titles: old {len(ot)}, new {len(nt)}; missing on new: {missing}")
print(f"row titles only on new: {sorted(set(nt) - set(ot))}")
if missing: bad = True
if "--table" in sys.argv:
    print("\n| Row | Old screen | New screen(s) |\n|---|---|---|")
    for t in sorted(ot):
        print(f"| {t} | {', '.join(sorted(set(ot[t])))} | {', '.join(sorted(set(nt.get(t, ['MISSING']))))} |")
sys.exit(1 if bad else 0)
