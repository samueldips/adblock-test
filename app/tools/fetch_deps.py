#!/usr/bin/env python3
"""Small Maven POM resolver + downloader for the manual Android build pipeline.
Downloads aar/jar/pom for each root artifact and its transitive compile/runtime deps.
Usage: python3 fetch_deps.py <libs_dir>
"""
import os, sys, re, urllib.request, xml.etree.ElementTree as ET

REPOS = [
    "https://repo1.maven.org/maven2",
    "https://dl.google.com/dl/android/maven2",
    "https://android-sdk.is.com",
]

# (group, artifact, version, repo_hint_index_or_None)
ROOTS = [
    ("com.google.android.gms", "play-services-ads", "25.5.0", 1),
    ("com.unity3d.ads", "unity-ads", "4.21.0", None),
    ("com.ironsource.sdk", "mediationsdk", "9.2.0", 2),
    ("com.inmobi.monetization", "inmobi-ads", "10.1.4", None),
    ("com.chartboost", "chartboost-sdk", "9.2.1", None),
    ("com.startapp", "inapp-sdk", "5.3.2", None),
    ("com.vungle", "vungle-ads", "7.7.9", None),
    ("com.google.android.play", "app-update", "2.1.0", 1),
    ("com.google.android.play", "review", "2.0.2", 1),
]

NS = {"m": "http://maven.apache.org/POM/4.0.0"}

def gpath(group, artifact, version, fname):
    return f"{group.replace('.', '/')}/{artifact}/{version}/{fname}"

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()

def resolve_pom(group, artifact, version, repo_hint):
    repos = ([REPOS[repo_hint]] if repo_hint is not None else []) + [r for r in REPOS if repo_hint is None or r != REPOS[repo_hint]]
    for repo in repos:
        url = f"{repo}/{gpath(group, artifact, version, f'{artifact}-{version}.pom')}"
        try:
            return fetch(url), repo
        except Exception:
            continue
    return None, None

def parse_deps(pom_bytes):
    root = ET.fromstring(pom_bytes)
    props = {}
    for p in root.findall("m:properties", NS):
        for c in p:
            tag = c.tag.split("}")[-1]
            props[tag] = (c.text or "").strip()
    parent = root.find("m:parent", NS)
    deps = []
    for d in root.findall("m:dependencies/m:dependency", NS):
        def t(name):
            e = d.find(f"m:{name}", NS)
            return (e.text or "").strip() if e is not None else ""
        scope = t("scope") or "compile"
        optional = t("optional") == "true"
        typ = t("type") or "jar"
        if scope in ("test", "provided") or optional:
            continue
        g, a, v = t("groupId"), t("artifactId"), t("version")
        for k, val in props.items():
            v = v.replace("${" + k + "}", val)
        # handle Maven version ranges like [25.5.0] or [1.0,2.0): take the lower bound
        if v.startswith("[") or v.startswith("("):
            v = re.split(r"[\],\)]", v[1:])[0].split(",")[0].strip()
        if v.startswith("${"):
            continue  # unresolvable property, skip
        deps.append((g, a, v, typ))
    return deps, parent

def ver_key(v):
    """Comparable version key. Non-numeric parts sort lower."""
    return tuple(int(x) for x in re.findall(r'\d+', v))

def main():
    libs = sys.argv[1] if len(sys.argv) > 1 else "libs"
    os.makedirs(libs, exist_ok=True)
    # Pass 1: BFS the full dependency graph, keeping the NEWEST version seen
    # for each (group, artifact) -- this is what Gradle does. First-wins
    # silently mixes incompatible versions (e.g. lifecycle-runtime 2.0.0
    # with lifecycle-runtime-ktx 2.6.1) and causes runtime crashes like
    # NoSuchFieldError.
    max_ver = {}   # (g,a) -> version
    max_hint = {}  # (g,a) -> repo hint
    parsed = set() # (g,a,v) POMs already expanded
    queue = list(ROOTS)
    while queue:
        g, a, v, hint = queue.pop(0)
        if (g, a) not in max_ver or ver_key(v) > ver_key(max_ver[(g, a)]):
            max_ver[(g, a)] = v
            max_hint[(g, a)] = hint
        if (g, a, v) in parsed:
            continue
        parsed.add((g, a, v))
        pom, repo = resolve_pom(g, a, v, hint)
        if pom is None:
            print(f"!! POM NOT FOUND: {g}:{a}:{v}")
            continue
        deps, parent = parse_deps(pom)
        if parent is not None:
            def t(n):
                e = parent.find(f"m:{n}", NS)
                return (e.text or "").strip() if e is not None else ""
            pg, pa, pv = t("groupId"), t("artifactId"), t("version")
            if pg and pa and pv and not pv.startswith("${"):
                queue.append((pg, pa, pv, hint))
        for dg, da, dv, typ in deps:
            queue.append((dg, da, dv, None))
    # Pass 2: download exactly the resolved (newest) versions.
    order = []
    for (g, a) in sorted(max_ver):
        v, hint = max_ver[(g, a)], max_hint[(g, a)]
        pom, repo = resolve_pom(g, a, v, hint)
        if repo is None:
            print(f"!! REPO NOT FOUND for {g}:{a}:{v}")
            continue
        order.append((g, a, v, repo))
    print(f"resolved {len(order)} artifacts")
    manifest = []
    for g, a, v, repo in order:
        base = f"{repo}/{gpath(g, a, v, '')}".rstrip("/")
        got = []
        for ext in ("aar", "jar"):
            dest = os.path.join(libs, f"{a}-{v}.{ext}")
            if os.path.exists(dest):
                got.append(ext)
                continue
            try:
                data = fetch(f"{base}/{a}-{v}.{ext}")
                with open(dest, "wb") as f:
                    f.write(data)
                got.append(ext)
                print(f"  ok {a}-{v}.{ext} ({len(data)//1024} KB)")
            except Exception:
                pass
        # also save pom for reference
        pdest = os.path.join(libs, f"{a}-{v}.pom")
        if not os.path.exists(pdest):
            try:
                data = fetch(f"{base}/{a}-{v}.pom")
                with open(pdest, "wb") as f:
                    f.write(data)
            except Exception:
                pass
        if not got:
            print(f"!! NO ARTIFACT: {g}:{a}:{v}")
        manifest.append(f"{g}:{a}:{v} [{','.join(got)}] <- {repo}")
    with open(os.path.join(libs, "ARTIFACTS.txt"), "w") as f:
        f.write("\n".join(manifest) + "\n")
    print("done.")

if __name__ == "__main__":
    main()
