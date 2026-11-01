#!/usr/bin/env python3
"""Skywyrms (Dragons) build: one preprocessor source tree -> one jar per loader and Minecraft version.

    python3 build/build.py --all                     # all 68 release jars -> dist/jars/
    python3 build/build.py --loader forge --mc 1.20.1
    python3 build/build.py --all --define AUTOTEST   # test jars (with the in-game AutoTest) -> dist/test-jars/
    python3 build/build.py --loader fabric --all     # every target of one loader

Fabric: a generated Loom project per target (Mojang names, Loom remaps to intermediary for <= 1.21.11).
Forge / NeoForge: javac against the Mojang-named Minecraft jar (taken from Loom's cache, so the Fabric target of the
same version is built first) + the loader's libraries read from its official installer; Forge 1.20 - 1.20.4 run with
SRG names, so their classes are remapped named -> SRG with tiny-remapper.
"""
import argparse, json, os, re, shutil, subprocess, sys, time, urllib.request, zipfile, io, hashlib, glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
B = os.path.join(ROOT, "build")
SRC = os.path.join(ROOT, "src")
CACHE = os.path.join(B, "cache")
WORK = os.path.join(B, "work")
JDK25 = "/opt/jdk/jdk-25.0.5+8"
GRADLE = "/opt/jdk/gradle-9.8.0/bin/gradle"
LOOM = "1.18.4"
FABRIC_LOADER = "0.19.5"
UA = {"User-Agent": "Mozilla/5.0"}
sys.path.insert(0, B)
import preprocess  # noqa: E402


def props():
    d = {}
    for line in open(os.path.join(B, "mod.properties")):
        if "=" in line and not line.startswith("#"):
            k, v = line.strip().split("=", 1)
            d[k] = v
    return d


def mcnum(v):
    p = [int(x) for x in v.split(".")] + [0, 0]
    return p[0] * 10000 + p[1] * 100 + p[2]


def java_release(mc):
    n = mcnum(mc)
    return 25 if n >= 260000 else 21 if n >= 12005 else 17


def targets():
    out = []
    for t in os.listdir(os.path.join(B, "meta")):
        l, v = t.split("-", 1)
        out.append((l, v))
    return sorted(out, key=lambda x: (("fabric", "forge", "neoforge").index(x[0]), mcnum(x[1])))


def fetch(url, path):
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    for attempt in range(4):
        try:
            data = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120).read()
            tmp = path + ".part"
            open(tmp, "wb").write(data)
            os.replace(tmp, path)
            return path
        except Exception as e:  # noqa: BLE001
            err = e
            time.sleep(2 + attempt * 3)
    raise RuntimeError(f"download failed {url}: {err}")


def maven_path(coord):
    parts = coord.split("@")[0].split(":")
    g, a, v = parts[0], parts[1], parts[2]
    cls = parts[3] if len(parts) > 3 else None
    ext = coord.split("@")[1] if "@" in coord else "jar"
    return f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}{'-' + cls if cls else ''}.{ext}"


# ------------------------------------------------------------------ sources and resources
def meta_text(loader, mc, rel, version):
    s = open(os.path.join(B, "meta", f"{loader}-{mc}", rel), encoding="utf-8").read()
    if rel.endswith("fabric.mod.json"):
        s = re.sub(r'"version": "[^"]+"', f'"version": "{version}"', s, count=1)
    elif rel.endswith(".toml"):
        s = re.sub(r'(\[\[mods\]\][^\[]*?version=")[^"]+"', lambda m: m.group(1) + version + '"', s, count=1, flags=re.S)
    return s


def stage(loader, mc, defines, wdir):
    """preprocessed java -> wdir/src/main/java, resources + metadata -> wdir/src/main/resources"""
    jdir = os.path.join(wdir, "src", "main", "java")
    rdir = os.path.join(wdir, "src", "main", "resources")
    shutil.rmtree(os.path.join(wdir, "src"), ignore_errors=True)
    preprocess.tree(SRC, loader, mc, jdir, defines)
    shutil.copytree(os.path.join(SRC, "resources", "common"), rdir)
    if mcnum(mc) < 12100:
        shutil.rmtree(os.path.join(rdir, "data", "skywyrms", "loot_table"), ignore_errors=True)
        shutil.copytree(os.path.join(SRC, "resources", "legacy_loot", "data"), os.path.join(rdir, "data"), dirs_exist_ok=True)
    version = props()["mod_version"]
    mdir = os.path.join(B, "meta", f"{loader}-{mc}")
    for root, _, fs in os.walk(mdir):
        for f in fs:
            rel = os.path.relpath(os.path.join(root, f), mdir)
            if rel == os.path.join("META-INF", "MANIFEST.MF"):
                continue
            q = os.path.join(rdir, rel)
            os.makedirs(os.path.dirname(q), exist_ok=True)
            open(q, "w", encoding="utf-8").write(meta_text(loader, mc, rel, version))
    return jdir, rdir


# ------------------------------------------------------------------ fabric (Loom)
def fabric_api_version(mc):
    fm = json.load(open(os.path.join(B, "meta", f"fabric-{mc}", "fabric.mod.json")))
    v = fm["depends"]["fabric-api"].lstrip(">=")
    return f"{v}+{mc}"


def build_fabric(mc, defines, out):
    wdir = os.path.join(WORK, f"fabric-{mc}" + ("-" + "-".join(defines).lower() if defines else ""))
    os.makedirs(wdir, exist_ok=True)
    stage("fabric", mc, defines, wdir)
    obf = mcnum(mc) < 260000
    open(os.path.join(wdir, "settings.gradle"), "w").write(
        "pluginManagement { repositories { maven { url = 'https://maven.fabricmc.net/' }; gradlePluginPortal() } }\n"
        f"rootProject.name = 'skywyrms-fabric-{mc}'\n")
    deps = ("modImplementation" if obf else "implementation")
    open(os.path.join(wdir, "build.gradle"), "w").write(f"""plugins {{ id 'net.fabricmc.fabric-loom{'-remap' if obf else ''}' version '{LOOM}' }}
base {{ archivesName = 'skywyrms' }}
version = '{props()['mod_version']}'
repositories {{ mavenCentral() }}
dependencies {{
    minecraft "com.mojang:minecraft:{mc}"
    {"mappings loom.officialMojangMappings()" if obf else ""}
    {deps} "net.fabricmc:fabric-loader:{FABRIC_LOADER}"
    {deps} "net.fabricmc.fabric-api:fabric-api:{fabric_api_version(mc)}"
}}
tasks.withType(JavaCompile).configureEach {{ options.release = {java_release(mc)}; options.encoding = 'UTF-8'; options.compilerArgs << '-parameters' }}
tasks.named('processResources') {{ duplicatesStrategy = DuplicatesStrategy.INCLUDE }}
""")
    open(os.path.join(wdir, "gradle.properties"), "w").write("org.gradle.jvmargs=-Xmx2500m\norg.gradle.caching=false\n")
    env = dict(os.environ, JAVA_HOME=JDK25)
    r = subprocess.run([GRADLE, "-q", "--console=plain", "build", "-x", "test"], cwd=wdir, env=env, capture_output=True, text=True)
    if r.returncode != 0:
        return False, (r.stdout + r.stderr)
    libs = [p for p in glob.glob(os.path.join(wdir, "build", "libs", "*.jar")) if not p.endswith("-sources.jar")]
    shutil.copy(libs[0], out)
    return True, ""


def loom_mc_jar(mc):
    """The Mojang-named Minecraft jar Loom made for this version (merged client+server)."""
    base = os.path.expanduser("~/.gradle/caches/fabric-loom")
    if mcnum(mc) >= 260000:
        pats = [f"{base}/minecraftMaven/net/minecraft/minecraft-merged/{mc}/minecraft-merged-{mc}.jar",
                f"{base}/minecraftMaven/net/minecraft/minecraft-*/{mc}*/minecraft-*{mc}*.jar"]
    else:
        pats = [f"{base}/minecraftMaven/net/minecraft/minecraft-merged/{mc}-*mojang*/minecraft-merged-{mc}-*.jar",
                f"{base}/minecraftMaven/net/minecraft/minecraft-merged/{mc}-*/minecraft-merged-{mc}-*.jar"]
    for p in pats:
        hits = [h for h in glob.glob(p) if not h.endswith("-sources.jar")]
        hits = [h for h in hits if f"-{mc}." in h or f"-{mc}-" in h or h.endswith(f"-{mc}.jar")]
        if hits:
            return sorted(hits, key=len)[0]
    return None


# ------------------------------------------------------------------ minecraft + loader libraries
_manifest = None


def mc_version_json(mc):
    global _manifest
    p = os.path.join(CACHE, "mc", mc, "version.json")
    if not os.path.exists(p):
        if _manifest is None:
            _manifest = json.load(urllib.request.urlopen("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
        url = next(v["url"] for v in _manifest["versions"] if v["id"] == mc)
        fetch(url, p)
    return json.load(open(p))


def lib_jars(libraries, default_repo="https://libraries.minecraft.net/"):
    out = []
    for lib in libraries:
        rules = lib.get("rules")
        if rules and not any(r.get("action") == "allow" and (not r.get("os") or r["os"].get("name") == "linux") for r in rules):
            continue
        art = lib.get("downloads", {}).get("artifact")
        if art and art.get("url"):
            path = os.path.join(CACHE, "libs", art["path"])
            fetch(art["url"], path)
            out.append(path)
        elif "name" in lib and not art:
            mp = maven_path(lib["name"])
            repo = lib.get("url", default_repo)
            path = os.path.join(CACHE, "libs", mp)
            try:
                fetch(repo.rstrip("/") + "/" + mp, path)
                out.append(path)
            except RuntimeError:
                pass
    return out


def loader_version(loader, mc):
    f = os.path.join(B, "meta", f"{loader}-{mc}", "META-INF", "neoforge.mods.toml" if loader == "neoforge" and mcnum(mc) >= 12005 else "mods.toml")
    s = open(f).read()
    v = re.search(r'modId="(?:forge|neoforge)".*?versionRange="\[([^,\]]+)', s, re.S).group(1)
    if loader == "forge" and v.count(".") < 2:  # "47.1" -> newest 47.1.x
        meta = urllib.request.urlopen(urllib.request.Request("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml", headers=UA)).read().decode()
        cands = [x.split("-", 1)[1] for x in re.findall(r"<version>([^<]+)</version>", meta) if x.startswith(mc + "-" + v + ".")]
        v = max(cands, key=lambda x: [int(p) for p in re.findall(r"\d+", x)])
    return v


def loader_jars(loader, mc):
    v = loader_version(loader, mc)
    if loader == "forge":
        coord = f"net.minecraftforge:forge:{mc}-{v}"
        repo = "https://maven.minecraftforge.net/"
    else:
        coord = f"net.neoforged:neoforge:{v}"
        repo = "https://maven.neoforged.net/releases/"
    inst = os.path.join(CACHE, "installers", maven_path(coord + ":installer"))
    fetch(repo + maven_path(coord + ":installer"), inst)
    z = zipfile.ZipFile(inst)
    jars = []
    for name in ("install_profile.json", "version.json"):
        if name in z.namelist():
            j = json.loads(z.read(name))
            jars += lib_jars(j.get("libraries", []), repo)
    # the loader's own API classes
    for cls in ("universal", None):
        c = coord + (":" + cls if cls else "")
        p = os.path.join(CACHE, "libs", maven_path(c))
        try:
            fetch(repo + maven_path(c), p)
            jars.append(p)
            break
        except RuntimeError:
            continue
    # jars nested in the installer (maven/ folder)
    for n in z.namelist():
        if n.startswith("maven/") and n.endswith(".jar"):
            p = os.path.join(CACHE, "libs", n[len("maven/"):])
            if not os.path.exists(p):
                os.makedirs(os.path.dirname(p), exist_ok=True)
                open(p, "wb").write(z.read(n))
            jars.append(p)
    return v, sorted(set(jars))


# ------------------------------------------------------------------ forge / neoforge (javac)
def tiny_remapper():
    p = os.path.join(CACHE, "tools", "tiny-remapper-0.11.1-fat.jar")
    return fetch("https://maven.fabricmc.net/net/fabricmc/tiny-remapper/0.11.1/tiny-remapper-0.11.1-fat.jar", p)


def srg_tiny(mc):
    """tiny v2 'named srg' mapping built from Mojang mappings + MCPConfig joined.tsrg"""
    out = os.path.join(CACHE, "mappings", mc, "named-srg.tiny")
    if os.path.exists(out):
        return out
    vj = mc_version_json(mc)
    pg = fetch(vj["downloads"]["client_mappings"]["url"], os.path.join(CACHE, "mappings", mc, "client.txt"))
    meta = urllib.request.urlopen(urllib.request.Request("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/maven-metadata.xml", headers=UA)).read().decode()
    allv = re.findall(r"<version>([^<]+)</version>", meta)
    pick = mc if mc in allv else [x for x in allv if x.startswith(mc + "-")][-1]
    zp = fetch(f"https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/{pick}/mcp_config-{pick}.zip", os.path.join(CACHE, "mappings", mc, "mcp_config.zip"))
    tsrg = zipfile.ZipFile(zp).read("config/joined.tsrg").decode().split("\n")
    # proguard: named <-> official
    n2o, o2n = {}, {}
    pf, pm = {}, {}
    cur = None
    prim = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S", "int": "I", "long": "J", "float": "F", "double": "D"}
    lines = open(pg, encoding="utf-8").read().split("\n")
    for line in lines:
        if line and not line.startswith("#") and not line.startswith(" "):
            a, b = line.rstrip(":").split(" -> ")
            n2o[a] = b
            o2n[b] = a

    def d_named(t):
        arr = 0
        while t.endswith("[]"):
            arr += 1
            t = t[:-2]
        return "[" * arr + (prim.get(t) or "L" + t.replace(".", "/") + ";")

    for line in lines:
        if not line or line.startswith("#"):
            continue
        if not line.startswith(" "):
            cur = line.rstrip(":").split(" -> ")
            continue
        left, right = line.strip().split(" -> ")
        left = re.sub(r"^\d+:\d+:", "", left)
        if "(" in left:
            ret, rest = left.split(" ", 1)
            name, args = rest.split("(", 1)
            args = args.rstrip(")")
            desc = "(" + "".join(d_named(a) for a in args.split(",") if a) + ")" + d_named(ret)
            pm[(cur[1], right, len([a for a in args.split(",") if a]), )] = pm.get((cur[1], right, len([a for a in args.split(",") if a])), []) + [(name, desc)]
        else:
            typ, name = left.split(" ")
            pf[(cur[1], right)] = (name, d_named(typ))

    def o_desc_to_named(desc):
        return re.sub(r"L([^;]+);", lambda m: "L" + o2n.get(m.group(1).replace("/", "."), m.group(1).replace("/", ".")).replace(".", "/") + ";", desc)

    rows = ["tiny\t2\t0\tnamed\tsrg"]
    owner = None
    by_cls = {}
    for line in tsrg[1:]:
        if not line.strip():
            continue
        if not line.startswith("\t"):
            owner = line.split()[0]
            continue
        if line.startswith("\t\t"):
            continue
        p = line.strip().split(" ")
        ocls = owner.replace("/", ".")
        ncls = o2n.get(ocls)
        if not ncls:
            continue
        if len(p) == 3:
            f = pf.get((ocls, p[0]))
            if f:
                by_cls.setdefault(ncls, []).append(f"\tf\t{f[1]}\t{f[0]}\t{p[1]}")
        elif len(p) == 4:
            nd = o_desc_to_named(p[1])
            argc = len(re.findall(r"\[*(?:[ZBCSIJFD]|L[^;]+;)", nd.split(")")[0][1:]))
            for name, desc in pm.get((ocls, p[0], argc), []):
                if desc == nd:
                    by_cls.setdefault(ncls, []).append(f"\tm\t{nd}\t{name}\t{p[2]}")
                    break
    for c, mem in by_cls.items():
        n = c.replace(".", "/")
        rows.append(f"c\t{n}\t{n}")
        rows.extend(mem)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    open(out, "w").write("\n".join(rows) + "\n")
    return out


def build_forgelike(loader, mc, defines, out):
    wdir = os.path.join(WORK, f"{loader}-{mc}" + ("-" + "-".join(defines).lower() if defines else ""))
    os.makedirs(wdir, exist_ok=True)
    jdir, rdir = stage(loader, mc, defines, wdir)
    mcjar = loom_mc_jar(mc)
    if not mcjar:
        return False, f"no Mojang-named Minecraft {mc} jar in Loom's cache: build fabric {mc} first"
    vj = mc_version_json(mc)
    cp = [mcjar] + lib_jars(vj["libraries"])
    lv, ljars = loader_jars(loader, mc)
    cp[0] = at_jar(loader, lv, mc, mcjar, ljars)
    cp += ljars
    classes = os.path.join(wdir, "classes")
    shutil.rmtree(classes, ignore_errors=True)
    os.makedirs(classes)
    srcs = [os.path.join(r, f) for r, _, fs in os.walk(jdir) for f in fs if f.endswith(".java")]
    argfile = os.path.join(wdir, "javac.args")
    open(argfile, "w").write("\n".join(["-cp", ":".join(cp), "-d", classes, "--release", str(java_release(mc)), "-encoding", "UTF-8",
                                        "-g", "-parameters", "-nowarn", "-proc:none", "-Xlint:none"] + srcs))
    r = subprocess.run([os.path.join(JDK25, "bin", "javac"), "@" + argfile], capture_output=True, text=True)
    if r.returncode != 0:
        return False, r.stdout + r.stderr
    if loader == "forge" and mcnum(mc) <= 12004:  # SRG runtime names
        rem = os.path.join(wdir, "classes-srg.jar")
        tmpjar = os.path.join(wdir, "classes-named.jar")
        zipdir(classes, tmpjar)
        r = subprocess.run(["java", "-jar", tiny_remapper(), tmpjar, rem, srg_tiny(mc), "named", "srg", mcjar] + [j for j in cp[1:]],
                           capture_output=True, text=True)
        if r.returncode != 0:
            return False, r.stdout + r.stderr
        shutil.rmtree(classes)
        os.makedirs(classes)
        zipfile.ZipFile(rem).extractall(classes)
    # jar
    manifest = (f"Manifest-Version: 1.0\nSpecification-Title: skywyrms\nSpecification-Vendor: pokato\nSpecification-Version: 1\n"
                f"Implementation-Title: Skywyrms\nImplementation-Version: {props()['mod_version']}\nImplementation-Vendor: pokato\n\n")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("META-INF/MANIFEST.MF", manifest)
        for base in (classes, rdir):
            for root, _, fs in os.walk(base):
                for f in sorted(fs):
                    p = os.path.join(root, f)
                    z.write(p, os.path.relpath(p, base))
    return True, ""


def at_jar(loader, lv, mc, mcjar, ljars):
    """Minecraft jar with the loader's access transformers applied (compile classpath only)."""
    import atjar
    out = os.path.join(CACHE, "atjars", f"{loader}-{lv}.jar")
    if os.path.exists(out):
        return out
    texts = []
    for j in ljars:
        if re.search(r"/(forge|neoforge)-[^/]*-universal\.jar$", j) or re.search(r"/neoforge/[^/]+/neoforge-[^/]+\.jar$", j):
            z = zipfile.ZipFile(j)
            for n in z.namelist():
                if n.endswith("accesstransformer.cfg"):
                    texts.append(z.read(n).decode("utf-8", "replace"))
    rename = None
    if loader == "forge" and mcnum(mc) <= 12004:
        rename = {}
        for line in open(srg_tiny(mc)):
            p = line.rstrip("\n").split("\t")
            if len(p) == 5 and p[1] in ("f", "m"):
                rename[p[4]] = p[3]
    os.makedirs(os.path.dirname(out), exist_ok=True)
    atjar.apply(mcjar, out + ".part", texts, rename)
    os.replace(out + ".part", out)
    return out


def zipdir(d, out):
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for root, _, fs in os.walk(d):
            for f in fs:
                p = os.path.join(root, f)
                z.write(p, os.path.relpath(p, d))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--loader")
    ap.add_argument("--mc")
    ap.add_argument("--define", action="append", default=[])
    a = ap.parse_args()
    sel = [t for t in targets() if (not a.loader or t[0] == a.loader) and (a.all or not a.mc or t[1] == a.mc)]
    if a.mc and not a.all:
        sel = [t for t in sel if t[1] == a.mc]
    outdir = os.path.join(ROOT, "dist", "test-jars" if "AUTOTEST" in a.define else "jars")
    os.makedirs(outdir, exist_ok=True)
    ver = props()["mod_version"]
    ok = 0
    for loader, mc in sel:
        t0 = time.time()
        out = os.path.join(outdir, f"skywyrms-{ver}-{loader}-{mc}.jar")
        try:
            good, log = (build_fabric if loader == "fabric" else lambda m, d, o: build_forgelike(loader, m, d, o))(mc, a.define, out)
        except Exception as e:  # noqa: BLE001
            good, log = False, repr(e)
        dt = time.time() - t0
        if good:
            ok += 1
            print(f"OK   {loader:8s} {mc:9s} {dt:6.1f}s  {os.path.relpath(out, ROOT)}", flush=True)
        else:
            print(f"FAIL {loader:8s} {mc:9s} {dt:6.1f}s", flush=True)
            errs = [l for l in log.split("\n") if "error:" in l or "rror" in l]
            print("\n".join("  " + l for l in (errs or log.split("\n"))[:25]), flush=True)
    print(f"{ok}/{len(sel)} targets OK")
    sys.exit(0 if ok == len(sel) else 1)


if __name__ == "__main__":
    main()
