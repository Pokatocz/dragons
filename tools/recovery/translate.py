"""Translate decompiled sources from intermediary (Fabric <= 1.21.11) or SRG members (Forge <= 1.20.4) to Mojang names.

translate.py <dec dir> <out dir>   (processes every target folder; mojang-named targets are copied unchanged)
"""
import json, os, re, shutil, sys

DEC, OUT = sys.argv[1], sys.argv[2]
MAPS = "/opt/recovery/maps"
_cache = {}


def maps(ver, kind):
    k = (ver, kind)
    if k not in _cache:
        _cache[k] = json.load(open(os.path.join(MAPS, ver, kind + "2named.json")))
    return _cache[k]


FQN = re.compile(r"\bnet\.minecraft\.(class_\d+(?:\.class_\d+)*)\b")
CHAIN = re.compile(r"\b(class_\d+(?:\.class_\d+)*)\b")
MEMBER_I = re.compile(r"\b(method_\d+|field_\d+|comp_\d+)\b")
MEMBER_S = re.compile(r"\b([mf]_\d+_)\b")


def tr_inter(src, m):
    classes = {k.split("/")[-1].split("$")[-1]: v for k, v in m["classes"].items()}
    members = m["members"]
    miss = set()

    def simple(tok):
        n = classes.get(tok)
        if n is None:
            miss.add(tok)
            return tok
        return n.split(".")[-1].split("$")[-1]

    def fqn(mo):
        last = mo.group(1).split(".")[-1]
        n = classes.get(last)
        if n is None:
            miss.add(last)
            return mo.group(0)
        return n.replace("$", ".")

    src = FQN.sub(fqn, src)
    src = CHAIN.sub(lambda mo: ".".join(simple(t) for t in mo.group(1).split(".")), src)

    def mem(mo):
        n = members.get(mo.group(1))
        if n is None:
            miss.add(mo.group(1))
            return mo.group(1)
        return n

    src = MEMBER_I.sub(mem, src)
    return src, miss


def tr_srg(src, m):
    members = m["members"]
    miss = set()

    def mem(mo):
        n = members.get(mo.group(1))
        if n is None:
            miss.add(mo.group(1))
            return mo.group(1)
        return n

    return MEMBER_S.sub(mem, src), miss


def main():
    report = {}
    for t in sorted(os.listdir(DEC)):
        if t.endswith(".tmp"):
            continue
        loader, ver = t.split("-", 1)
        kind = None
        mc = [int(x) for x in ver.split(".")]
        if mc[0] == 1 and loader == "fabric":
            kind = "inter"
        elif mc[0] == 1 and loader == "forge" and mc[1] == 20 and (len(mc) < 3 or mc[2] <= 4):
            kind = "srg"
        miss_all = set()
        for root, _, files in os.walk(os.path.join(DEC, t)):
            for f in files:
                if not f.endswith(".java"):
                    continue
                p = os.path.join(root, f)
                rel = os.path.relpath(p, os.path.join(DEC, t))
                q = os.path.join(OUT, t, rel)
                os.makedirs(os.path.dirname(q), exist_ok=True)
                src = open(p, encoding="utf-8").read()
                if kind == "inter":
                    src, miss = tr_inter(src, maps(ver, "inter"))
                elif kind == "srg":
                    src, miss = tr_srg(src, maps(ver, "srg"))
                else:
                    miss = set()
                miss_all |= miss
                open(q, "w", encoding="utf-8").write(src)
        report[t] = sorted(miss_all)[:20]
        if miss_all:
            print(t, kind, "unmapped:", len(miss_all), sorted(miss_all)[:12])
    print("translated", len(report), "targets")


main()
