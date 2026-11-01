"""Rename intermediary (class_N/method_N/field_N/comp_N) or SRG (m_N_/f_N_) tokens inside class files.

These names are unique per game version, so rewriting every CONSTANT_Utf8 entry is enough (no hierarchy needed).
cprename.py <in.jar> <out.jar> <inter|srg> <mc version>
"""
import json, re, struct, sys, zipfile

MAPS = "/opt/recovery/maps"


def rewrite_class(data, fn):
    out = bytearray(data[:10])
    count = struct.unpack_from(">H", data, 8)[0]
    off = 10
    i = 1
    while i < count:
        tag = data[off]
        if tag == 1:
            ln = struct.unpack_from(">H", data, off + 1)[0]
            s = data[off + 3:off + 3 + ln]
            try:
                txt = s.decode("utf-8")
                new = fn(txt).encode("utf-8")
            except UnicodeDecodeError:
                new = s
            out += bytes([1]) + struct.pack(">H", len(new)) + new
            off += 3 + ln
        else:
            size = {3: 5, 4: 5, 5: 9, 6: 9, 7: 3, 8: 3, 9: 5, 10: 5, 11: 5, 12: 5, 15: 4, 16: 3, 17: 5, 18: 5, 19: 3, 20: 3}[tag]
            out += data[off:off + size]
            off += size
            if tag in (5, 6):
                i += 1
        i += 1
    out += data[off:]
    return bytes(out)


def main():
    src, dst, kind, ver = sys.argv[1:5]
    if kind == "inter":
        m = json.load(open(f"{MAPS}/{ver}/inter2named.json"))
        full = {k: v.replace(".", "/") for k, v in m["classes"].items()}  # net/minecraft/class_N(\$class_M) -> named
        last = {k.split("/")[-1].split("$")[-1]: v for k, v in full.items()}
        mem = m["members"]
        pat_cls = re.compile(r"net/minecraft/class_\d+(?:\$class_\d+)*")
        pat_tok = re.compile(r"\b(class_\d+|method_\d+|field_\d+|comp_\d+)\b")

        def fn(s):
            s = pat_cls.sub(lambda mo: last.get(mo.group(0).split("$")[-1].split("/")[-1], mo.group(0)), s)

            def tok(mo):
                t = mo.group(1)
                if t.startswith("class_"):
                    n = last.get(t)
                    return n.split("/")[-1].split("$")[-1] if n else t
                return mem.get(t, t)

            return pat_tok.sub(tok, s)
    else:
        mem = json.load(open(f"{MAPS}/{ver}/srg2named.json"))["members"]
        pat = re.compile(r"\b([mf]_\d+_)\b")

        def fn(s):
            return pat.sub(lambda mo: mem.get(mo.group(1), mo.group(1)), s)

    zin = zipfile.ZipFile(src)
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        for n in zin.namelist():
            d = zin.read(n)
            if n.endswith(".class"):
                d = rewrite_class(d, fn)
            zout.writestr(n, d)


main()
