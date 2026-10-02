#!/usr/bin/env python3
"""Dedupe dex inputs at class granularity.

For every .class entry defined in multiple jars, the jar with the highest
(version, -ktx preference) wins. Produces filtered copies of the losing jars
with the duplicated classes removed, so no unique class is ever lost.
Usage: dedupe_jars.py <input-list-file> <output-dir>
Prints the final jar paths (originals or filtered copies), one per line.
"""
import sys, zipfile, re, os, shutil

def version_key(fname):
    base = os.path.basename(fname)
    if base == 'classes.jar':
        base = os.path.basename(os.path.dirname(fname)) + '.jar'
    m = re.match(r'(.+?)-(\d[\d.]*)\.jar$', base)
    if not m:
        return (base, ())
    art, ver = m.group(1), m.group(2)
    nums = tuple(int(x) for x in re.findall(r'\d+', ver))
    ktx = 1 if art.endswith('-ktx') else 0
    return (art, nums, ktx, base)

def main():
    list_file, out_dir = sys.argv[1], sys.argv[2]
    os.makedirs(out_dir, exist_ok=True)
    with open(list_file) as f:
        jars = [l.strip() for l in f if l.strip().endswith('.jar')]

    jar_entries = {}   # jar -> list of all zip entry names
    jar_classes = {}   # jar -> set of .class entries
    class_to_jars = {}
    for j in jars:
        try:
            with zipfile.ZipFile(j) as z:
                names = z.namelist()
        except Exception as e:
            print(f"# unreadable {j}: {e}", file=sys.stderr)
            continue
        jar_entries[j] = names
        cls = {n for n in names if n.endswith('.class')}
        jar_classes[j] = cls
        for n in cls:
            class_to_jars.setdefault(n, []).append(j)

    # winner per duplicated class
    omit = {j: set() for j in jars}
    dup_classes = 0
    for cls, js in class_to_jars.items():
        if len(js) < 2:
            continue
        dup_classes += 1
        winner = max(js, key=version_key)
        for j in js:
            if j != winner:
                omit[j].add(cls)
    print(f"# {dup_classes} duplicated classes resolved", file=sys.stderr)

    final = []
    for j in jars:
        if j not in jar_entries:
            continue
        if not omit[j]:
            final.append(j)
            continue
        # filtered copy
        safe = re.sub(r'[^A-Za-z0-9_.-]', '_', j.replace(os.sep, '_'))
        dest = os.path.join(out_dir, safe)
        if not os.path.exists(dest):
            with zipfile.ZipFile(j, 'r') as zin, \
                 zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED) as zout:
                for item in zin.infolist():
                    if item.filename in omit[j]:
                        continue
                    zout.writestr(item, zin.read(item.filename))
        t = os.path.basename(j)
        if t == 'classes.jar':
            t = os.path.basename(os.path.dirname(j)) + '/classes.jar'
        print(f"# filtered {t}: removed {len(omit[j])} dup classes", file=sys.stderr)
        final.append(dest)
    print('\n'.join(final))

if __name__ == '__main__':
    main()
