"""Build token maps intermediary->Mojang and SRG->Mojang for one Minecraft version.

intermediary names (class_N, method_N, field_N, comp_N) and SRG member ids (m_N_, f_N_) are unique per game
version, so a decompiled source can be translated by plain token substitution.
Output: maps/<ver>/inter2named.json  {"classes": {"class_N": "net.minecraft.x.Y$Z"}, "members": {"method_N": "name"}}
        maps/<ver>/srg2named.json    {"members": {"m_N_": "name", "f_N_": "name"}}
"""
import json, os, re, sys

PRIM = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S", "int": "I", "long": "J", "float": "F", "double": "D"}


def parse_proguard(path):
    """Mojang client mappings -> (official->named class map, members keyed by (official owner, official name, official desc))."""
    classes_n2o, raw = {}, []
    cur = None
    for line in open(path, encoding="utf-8"):
        if line.startswith("#") or not line.strip():
            continue
        if not line.startswith(" "):
            named, obf = line.rstrip(":\n").split(" -> ")
            classes_n2o[named] = obf
            cur = (named, obf)
            raw_cur = []
            raw.append((cur, raw_cur))
        else:
            raw_cur.append(line.strip())
    o2n = {o: n for n, o in classes_n2o.items()}

    def desc_of(t):
        arr = 0
        while t.endswith("[]"):
            arr += 1
            t = t[:-2]
        d = PRIM.get(t)
        if d is None:
            d = "L" + classes_n2o.get(t, t).replace(".", "/") + ";"
        return "[" * arr + d

    fields, methods = {}, {}
    for (named, obf), lines in raw:
        for m in lines:
            left, right = m.split(" -> ")
            left = re.sub(r"^\d+:\d+:", "", left)
            if "(" in left:
                ret, rest = left.split(" ", 1)
                name, args = rest.split("(", 1)
                args = args.rstrip(")")
                argd = "".join(desc_of(a) for a in args.split(",")) if args else ""
                desc = "(" + argd + ")" + desc_of(ret)
                methods[(obf, right, desc)] = name
                methods.setdefault((obf, right, None, len(args.split(",")) if args else 0), name)
            else:
                typ, name = left.split(" ")
                fields[(obf, right)] = name
    return o2n, fields, methods


def build(ver, root):
    d = os.path.join(root, ver)
    o2n, fields, methods = parse_proguard(os.path.join(d, "client.txt"))
    out = {}
    # intermediary
    ti = os.path.join(d, "intermediary.tiny")
    if os.path.exists(ti):
        classes, members = {}, {}
        owner = None
        for line in open(ti, encoding="utf-8"):
            p = line.rstrip("\n").split("\t")
            if p[0] == "c":
                owner = p[1]
                n = o2n.get(owner.replace("/", "."))
                if n:
                    classes[p[2].split("/")[-1].split("$")[-1] if False else p[2]] = n
            elif len(p) >= 5 and p[1] == "f":
                n = fields.get((owner.replace("/", "."), p[3]))
                if n:
                    members[p[4]] = n
            elif len(p) >= 5 and p[1] == "m":
                n = methods.get((owner.replace("/", "."), p[3], p[2]))
                if n:
                    members[p[4]] = n
        out["inter"] = {"classes": classes, "members": members}
        json.dump(out["inter"], open(os.path.join(d, "inter2named.json"), "w"))
    ts = os.path.join(d, "joined.tsrg")
    if os.path.exists(ts):
        members = {}
        owner = None
        for line in open(ts, encoding="utf-8"):
            if line.startswith("tsrg2"):
                continue
            if not line.startswith("\t"):
                owner = line.split()[0]
                continue
            if line.startswith("\t\t"):
                continue  # parameters
            p = line.strip().split(" ")
            if len(p) == 3:  # field: obf srg id
                n = fields.get((owner.replace("/", "."), p[0]))
                if n:
                    members[p[1]] = n
            elif len(p) == 4:  # method: obf desc srg id
                n = methods.get((owner.replace("/", "."), p[0], p[1]))
                if n:
                    members[p[2]] = n
        out["srg"] = {"members": members}
        json.dump(out["srg"], open(os.path.join(d, "srg2named.json"), "w"))
    return {k: (len(v.get("classes", {})), len(v["members"])) for k, v in out.items()}


if __name__ == "__main__":
    root = sys.argv[1]
    for v in sys.argv[2:]:
        print(v, build(v, root))
