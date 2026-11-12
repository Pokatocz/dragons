#!/usr/bin/env python3
"""Mechanical checks on built jars: tools/audit/jarscan.py <version>
1. server safety: classes outside the mod's client package that refer to client-only classes
2. resources: json validity, translation keys used in code, sounds, textures and models that are referred to
3. metadata per loader
"""
import sys, zipfile, glob, os, re, json, struct, collections

VER = sys.argv[1]
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

def pool(data):
    off = 8; n = struct.unpack_from(">H", data, off)[0]; off += 2; cp = [None] * n; i = 1
    while i < n:
        tag = data[off]; off += 1
        if tag == 1:
            ln = struct.unpack_from(">H", data, off)[0]; off += 2
            cp[i] = ("utf8", data[off:off + ln].decode("utf-8", "replace")); off += ln
        elif tag in (3, 4): off += 4
        elif tag in (5, 6): off += 8; i += 1
        elif tag == 7: cp[i] = ("class", struct.unpack_from(">H", data, off)[0]); off += 2
        elif tag == 8: cp[i] = ("string", struct.unpack_from(">H", data, off)[0]); off += 2
        elif tag in (9, 10, 11, 12, 17, 18): off += 4
        elif tag == 15: off += 3
        elif tag in (16, 19, 20): off += 2
        else: raise ValueError(tag)
        i += 1
    classes = {cp[c[1]][1] for c in cp if c and c[0] == "class"}
    strings = {cp[c[1]][1] for c in cp if c and c[0] == "string"}
    utf = {c[1] for c in cp if c and c[0] == "utf8"}
    return classes, strings, utf

CLIENT_ONLY = ("net/minecraft/client/", "com/mojang/blaze3d/", "com/pokato/skywyrms/client/")
problems = collections.defaultdict(list)
jars = sorted(glob.glob(os.path.join(ROOT, "dist", "jars", f"skywyrms-{VER}-*.jar")))
print(len(jars), "jars")
for j in jars:
    name = os.path.basename(j)[len(f"skywyrms-{VER}-"):-4]
    loader = name.split("-")[0]
    z = zipfile.ZipFile(j)
    names = z.namelist()
    allstrings = set()
    for n in names:
        if not n.endswith(".class"): continue
        classes, strings, utf = pool(z.read(n))
        allstrings |= strings
        cls = n[:-6]
        clientside = cls.startswith("com/pokato/skywyrms/client/") or re.search(r"/(fabric|forge|neoforge)/Skywyrms\w*Client", cls)
        if not clientside:
            bad = sorted(c for c in classes if c.startswith(CLIENT_ONLY))
            # descriptors too (field and method types)
            bad += sorted({m for u in utf for m in re.findall(r"L(net/minecraft/client/[^;]+|com/pokato/skywyrms/client/[^;]+);", u)} - set(bad))
            if bad:
                problems["client classes referred to from " + cls].append((name, tuple(bad[:6])))
    # ---- resources
    files = set(names)
    for n in names:
        if n.endswith((".json", ".mcmeta")):
            try: json.loads(z.read(n).decode("utf-8"))
            except Exception as e: problems["invalid json " + n].append((name, str(e)[:60]))
    lang = json.loads(z.read("assets/skywyrms/lang/en_us.json"))
    for s in allstrings:
        if re.fullmatch(r"(item|entity|key|message|ability|itemGroup|subtitles|tooltip|info|trait|element|death\.attack|gui|key\.category)\.skywyrms[\w.]*", s) and s not in lang and not s.endswith("."):
            problems["translation key used in code but missing in en_us.json: " + s].append((name, ""))
    if "assets/skywyrms/sounds.json" in files:
        sounds = json.loads(z.read("assets/skywyrms/sounds.json"))
        for ev, d in sounds.items():
            for snd in d.get("sounds", []):
                sn = snd if isinstance(snd, str) else snd.get("name")
                ns, _, path = sn.partition(":") if ":" in sn else ("minecraft", "", sn)
                if ns == "skywyrms" and f"assets/skywyrms/sounds/{path}.ogg" not in files:
                    problems[f"sound file missing: {sn} ({ev})"].append((name, ""))
            sub = d.get("subtitle")
            if sub and sub not in lang: problems["subtitle missing in lang: " + sub].append((name, ""))
    for n in names:
        if n.startswith("assets/skywyrms/models/") and n.endswith(".json"):
            m = json.loads(z.read(n))
            for t in (m.get("textures") or {}).values():
                if isinstance(t, str) and t.startswith("skywyrms:") and f"assets/skywyrms/textures/{t[9:]}.png" not in files:
                    problems[f"texture missing: {t} in {n}"].append((name, ""))
    for s in allstrings:
        m = re.fullmatch(r"textures/[\w/]+\.png", s)
        if m and f"assets/skywyrms/{s}" not in files:
            problems["texture path in code but no file: " + s].append((name, ""))
    # ---- metadata
    meta = [n for n in ("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml") if n in files]
    if len(meta) != 1: problems["metadata files: " + str(meta)].append((name, ""))
    if "pack.mcmeta" not in files and loader != "fabric": problems["no pack.mcmeta"].append((name, ""))

if not problems: print("no problems")
for k, v in sorted(problems.items()):
    targets = sorted({t for t, _ in v})
    print(f"- {k}\n    in {len(targets)} jars: {' '.join(targets[:8])}{' ...' if len(targets) > 8 else ''}  {v[0][1] if v[0][1] else ''}")
