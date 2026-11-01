"""Merge per-target decompiled sources into one preprocessor source tree.

merge.py <mojdec dir> <out src dir>
Every target folder is <loader>-<mc>. Files under com/pokato/skywyrms/<loader>/ go to src/<loader>/java, the rest
to src/common/java. Lines that differ between targets are wrapped in //#if <cond> ... //#else ... //#endif, where cond
uses MC (e.g. 12001 = 1.20.1, 260100 = 26.1) and FABRIC / FORGE / NEOFORGE.
"""
import difflib, os, sys
from collections import defaultdict, OrderedDict

LOADERS = ("fabric", "forge", "neoforge")


def mcnum(v):
    p = [int(x) for x in v.split(".")] + [0, 0]
    return p[0] * 10000 + p[1] * 100 + p[2]


def target_key(t):
    l, v = t.split("-", 1)
    return (mcnum(v), LOADERS.index(l))


ALL_VERSIONS = []  # filled in main: sorted unique mc numbers of all targets


def runs_expr(in_set, out_set):
    """Expression over MC for versions: true on in_set, false on out_set (others don't care)."""
    seq = [v for v in ALL_VERSIONS if v in in_set or v in out_set]
    runs = []
    cur = None
    for v in seq:
        if v in in_set:
            if cur is None:
                cur = [v, v]
            else:
                cur[1] = v
        else:
            if cur is not None:
                runs.append((cur[0], v))  # [start, first excluded)
                cur = None
    if cur is not None:
        runs.append((cur[0], None))
    lo = seq[0] if seq else None
    parts = []
    for a, b in runs:
        c = []
        if a != lo:
            c.append(f"MC >= {a}")
        if b is not None:
            c.append(f"MC < {b}")
        parts.append(" && ".join(c) if c else "true")
    if not parts:
        return "false"
    if len(parts) == 1:
        return parts[0]
    return " || ".join(f"({p})" if "&&" in p else p for p in parts)


def cond(S, P):
    """Boolean expression that is true for targets in S and false for targets in P - S."""
    by_v = defaultdict(set)
    for t in P:
        l, v = t.split("-", 1)
        by_v[mcnum(v)].add(t)
    indep = all(ts <= S or not (ts & S) for ts in by_v.values())
    loaders_p = sorted({t.split("-")[0] for t in P}, key=LOADERS.index)
    if indep:
        ins = {v for v, ts in by_v.items() if ts <= S}
        outs = set(by_v) - ins
        return runs_expr(ins, outs)
    # loader split
    parts = []
    full = []
    for l in loaders_p:
        pl = {t for t in P if t.startswith(l + "-")}
        sl = pl & S
        if not sl:
            continue
        if sl == pl:
            full.append(l.upper())
            continue
        ins = {mcnum(t.split("-", 1)[1]) for t in sl}
        outs = {mcnum(t.split("-", 1)[1]) for t in pl - sl}
        e = runs_expr(ins, outs)
        parts.append(f"{l.upper()} && ({e})" if "||" in e else f"{l.upper()} && {e}")
    allp = full + parts
    return " || ".join(allp)


def weave(variants):
    """variants: list of (lines, frozenset targets) -> list of [line, set(targets)]"""
    variants = sorted(variants, key=lambda x: -len(x[1]))
    base_lines, base_t = variants[0]
    merged = [[ln, set(base_t)] for ln in base_lines]
    rest = sorted(variants[1:], key=lambda x: min(target_key(t) for t in x[1]), reverse=True)
    for lines, ts in rest:
        sm = difflib.SequenceMatcher(None, [m[0] for m in merged], lines, autojunk=False)
        out = []
        for op, i1, i2, j1, j2 in sm.get_opcodes():
            if op == "equal":
                for k in range(i2 - i1):
                    merged[i1 + k][1] |= ts
                    out.append(merged[i1 + k])
            else:
                out.extend(merged[i1:i2])
                out.extend([ln, set(ts)] for ln in lines[j1:j2])
        merged = out
    return merged


def emit(merged, P):
    P = frozenset(P)
    blocks = []
    for ln, ts in merged:
        ts = frozenset(ts)
        if blocks and blocks[-1][0] == ts:
            blocks[-1][1].append(ln)
        else:
            blocks.append([ts, [ln]])
    out = []
    i = 0
    while i < len(blocks):
        ts, lines = blocks[i]
        if ts == P:
            out.extend(lines)
            i += 1
            continue
        indent = ""
        first = next((l for l in lines if l.strip()), "")
        indent = first[: len(first) - len(first.lstrip())]
        out.append(f"{indent}//#if {cond(ts, P)}")
        out.extend(lines)
        j = i + 1
        covered = set(ts)
        # chain of disjoint blocks covering P -> #elif / #else
        while j < len(blocks) and blocks[j][0] != P and not (blocks[j][0] & covered):
            nts, nlines = blocks[j]
            covered |= nts
            if covered == P:
                out.append(f"{indent}//#else")
            else:
                out.append(f"{indent}//#elif {cond(nts, P)}")
            out.extend(nlines)
            j += 1
            if covered == P:
                break
        out.append(f"{indent}//#endif")
        i = j
    return out


def main():
    src, dst = sys.argv[1], sys.argv[2]
    targets = sorted([t for t in os.listdir(src) if not t.endswith(".tmp")], key=target_key)
    ALL_VERSIONS.extend(sorted({mcnum(t.split("-", 1)[1]) for t in targets}))
    files = defaultdict(dict)
    for t in targets:
        base = os.path.join(src, t)
        for root, _, fs in os.walk(base):
            for f in fs:
                if f.endswith(".java"):
                    rel = os.path.relpath(os.path.join(root, f), base)
                    files[rel][t] = [l.rstrip() for l in open(os.path.join(root, f), encoding="utf-8").read().split("\n")]
    stats = []
    for rel, per in sorted(files.items()):
        groups = defaultdict(set)
        texts = {}
        for t, lines in per.items():
            k = "\n".join(lines)
            groups[k].add(t)
            texts[k] = lines
        variants = [(texts[k], frozenset(ts)) for k, ts in groups.items()]
        merged = weave(variants)
        out = emit(merged, set(per))
        parts = rel.split(os.sep)
        loader = parts[3] if len(parts) > 4 and parts[3] in LOADERS else None
        root = os.path.join(dst, loader if loader else "common", "java")
        p = os.path.join(root, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, "w", encoding="utf-8").write("\n".join(out).rstrip() + "\n")
        nif = sum(1 for l in out if l.strip().startswith("//#if"))
        stats.append((nif, len(variants), len(out), rel))
    for nif, nv, n, rel in sorted(stats, reverse=True)[:60]:
        print(f"{nif:4d} ifs {nv:3d} variants {n:6d} lines  {rel}")
    print("files", len(stats))


if __name__ == "__main__":
    main()
