"""Apply Forge/NeoForge access transformers to a Mojang-named Minecraft jar (compile classpath only).

Only access flags change (class, InnerClasses entries, fields, methods); everything else is copied byte for byte.
"""
import re, struct, zipfile

ACC = {"public": 0x0001, "private": 0x0002, "protected": 0x0004, "default": 0}
MASK = 0x0007


def parse_at(text, rename=None):
    """-> {class_internal: {"cls": acc, "members": {name_or_name+desc: acc}}}; acc = (level, final_change)"""
    rules = {}
    for line in text.split("\n"):
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        a = parts[0]
        fin = None
        if a.endswith("-f"):
            a, fin = a[:-2], False
        elif a.endswith("+f"):
            a, fin = a[:-2], True
        if a not in ACC:
            continue
        cls = parts[1].replace(".", "/")
        r = rules.setdefault(cls, {"cls": None, "members": {}})
        if len(parts) == 2:
            r["cls"] = (a, fin)
        else:
            m = parts[2]
            if rename:
                m = re.sub(r"\b[mf]_\d+_\b", lambda mo: rename.get(mo.group(0), mo.group(0)), m)
            r["members"][m] = (a, fin)
    return rules


def widen(flags, rule):
    level, fin = rule
    cur = flags & MASK
    order = {0x0002: 0, 0: 1, 0x0004: 2, 0x0001: 3}
    new = ACC[level]
    if order.get(new, 0) > order.get(cur, 0):
        flags = (flags & ~MASK) | new
    if fin is False:
        flags &= ~0x0010
    elif fin is True:
        flags |= 0x0010
    return flags


def patch_class(data, rules_for_this, all_rules):
    buf = bytearray(data)
    count = struct.unpack_from(">H", data, 8)[0]
    off = 10
    cp = [None] * count
    i = 1
    while i < count:
        tag = data[off]
        if tag == 1:
            ln = struct.unpack_from(">H", data, off + 1)[0]
            cp[i] = ("utf8", data[off + 3:off + 3 + ln].decode("utf-8", "replace"))
            off += 3 + ln
        else:
            size = {3: 5, 4: 5, 5: 9, 6: 9, 7: 3, 8: 3, 9: 5, 10: 5, 11: 5, 12: 5, 15: 4, 16: 3, 17: 5, 18: 5, 19: 3, 20: 3}[tag]
            if tag == 7:
                cp[i] = ("class", struct.unpack_from(">H", data, off + 1)[0])
            off += size
            if tag in (5, 6):
                i += 1
        i += 1
    utf = lambda k: cp[k][1]
    clsname = lambda k: utf(cp[k][1]) if k else None
    acc_off = off
    this = clsname(struct.unpack_from(">H", data, off + 2)[0])
    r = rules_for_this
    if r and r["cls"]:
        f = struct.unpack_from(">H", buf, acc_off)[0]
        struct.pack_into(">H", buf, acc_off, widen(f, r["cls"]) & ~0x0002 & ~0x0004)
    off += 6
    ni = struct.unpack_from(">H", data, off)[0]
    off += 2 + 2 * ni

    def members(off, is_method):
        n = struct.unpack_from(">H", data, off)[0]
        off += 2
        for _ in range(n):
            fl, ni_, di, ac = struct.unpack_from(">HHHH", data, off)
            name, desc = utf(ni_), utf(di)
            if r:
                rule = r["members"].get(name + desc if is_method else name) or r["members"].get(name)
                if rule is None:
                    rule = r["members"].get("*()" if is_method else "*")
                if rule:
                    struct.pack_into(">H", buf, off, widen(fl, rule))
            off += 8
            for _ in range(ac):
                _x, ln = struct.unpack_from(">HI", data, off)
                off += 6 + ln
        return off

    off = members(off, False)
    off = members(off, True)
    # class attributes: InnerClasses
    na = struct.unpack_from(">H", data, off)[0]
    off += 2
    for _ in range(na):
        ni_, ln = struct.unpack_from(">HI", data, off)
        if utf(ni_) == "InnerClasses":
            n = struct.unpack_from(">H", data, off + 6)[0]
            p = off + 8
            for _ in range(n):
                inner, outer, iname, fl = struct.unpack_from(">HHHH", data, p)
                rr = all_rules.get(clsname(inner))
                if rr and rr["cls"]:
                    struct.pack_into(">H", buf, p + 6, widen(fl, rr["cls"]))
                p += 8
        off += 6 + ln
    return bytes(buf)


def apply(src_jar, out_jar, at_texts, rename=None):
    rules = {}
    for t in at_texts:
        for cls, r in parse_at(t, rename).items():
            cur = rules.setdefault(cls, {"cls": None, "members": {}})
            cur["cls"] = r["cls"] or cur["cls"]
            cur["members"].update(r["members"])
    outer_of = {c.split("$")[0] for c in rules if "$" in c}
    zin = zipfile.ZipFile(src_jar)
    with zipfile.ZipFile(out_jar, "w", zipfile.ZIP_DEFLATED) as z:
        for n in zin.namelist():
            d = zin.read(n)
            if n.endswith(".class"):
                c = n[:-6]
                if c in rules or c in outer_of or any(k.startswith(c + "$") for k in rules if "$" in k):
                    d = patch_class(d, rules.get(c), rules)
            z.writestr(n, d)
    return len(rules)
