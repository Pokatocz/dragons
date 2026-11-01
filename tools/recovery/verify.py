"""Compare a rebuilt jar with the published 1.2.3 jar of the same target, at the decompiled-source level.

verify.py <rebuilt jar> <target>   -> prints differing files and a diff line count
"""
import os, subprocess, sys, difflib, tempfile, shutil

R = "/opt/recovery"  # decompiled reference + mappings
jar, t = sys.argv[1], sys.argv[2]
loader, ver = t.split("-", 1)
tmp = tempfile.mkdtemp()
src = jar
if loader == "fabric" and ver.startswith("1."):
    src = os.path.join(tmp, "moj.jar")
    subprocess.run(["python3", f"{R}/tools/cprename.py", jar, src, "inter", ver], check=True)
elif loader == "forge" and ver in ("1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4"):
    src = os.path.join(tmp, "moj.jar")
    subprocess.run(["python3", f"{R}/tools/cprename.py", jar, src, "srg", ver], check=True)
out = os.path.join(tmp, "dec")
os.makedirs(out)
subprocess.run(["java", "-Xmx1500m", "-jar", f"{R}/vineflower.jar", "-dgs=1", "-rsy=1", "--only=com/pokato", src, out],
               capture_output=True)


def norm(p):
    L = [l.rstrip() for l in open(p, encoding="utf-8").read().split("\n")]
    idx = [i for i, l in enumerate(L) if l.startswith("import ")]
    if idx:
        a, b = idx[0], idx[-1]
        if all(l.startswith("import ") or not l.strip() for l in L[a:b + 1]):
            L[a:b + 1] = sorted(set(l for l in L[a:b + 1] if l.strip()))
    while L and not L[-1]:
        L.pop()
    return L


ref = os.path.join(R, "mojdec", t)
total = 0
files = 0
for root, _, fs in os.walk(ref):
    for f in fs:
        if not f.endswith(".java"):
            continue
        a = os.path.join(root, f)
        rel = os.path.relpath(a, ref)
        b = os.path.join(out, rel)
        if not os.path.exists(b):
            print("MISSING", rel)
            total += 1
            continue
        d = [l for l in difflib.unified_diff(norm(a), norm(b), lineterm="", n=0) if l[:1] in "+-" and not l.startswith(("+++", "---"))]
        if d:
            files += 1
            total += len(d)
            print(f"{len(d):4d}  {rel}")
            if "-v" in sys.argv:
                print("\n".join("      " + x for x in d[:30]))
extra = [os.path.relpath(os.path.join(r, f), out) for r, _, fs in os.walk(out) for f in fs if f.endswith(".java")
         and not os.path.exists(os.path.join(ref, os.path.relpath(os.path.join(r, f), out)))]
for e in extra:
    print("EXTRA", e)
print(f"{t}: {files} files differ, {total} diff lines, {len(extra)} extra")
shutil.rmtree(tmp)
