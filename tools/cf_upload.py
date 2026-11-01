#!/usr/bin/env python3
"""Upload the release jars of one version to the CurseForge project "Dragons" (id 1711514) via the Upload API.

    CF_TOKEN_FILE=/path/to/token python3 tools/cf_upload.py 1.2.4 [--dry] [--only fabric-1.21.11,forge-1.20.1]

The token is read from the file named by CF_TOKEN_FILE (never from the command line, never printed).
Each jar gets: Minecraft version + loader (+ Client/Server environment), the changelog dist/changelog_<ver>.md,
display name = file name, release type "release".
"""
import json, os, sys, time, urllib.request, uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT = 1711514
API = "https://minecraft.curseforge.com/api"


def req(path, token, data=None, headers=None):
    h = {"X-Api-Token": token, "User-Agent": "Mozilla/5.0"}
    h.update(headers or {})
    r = urllib.request.Request(API + path, data=data, headers=h, method="POST" if data is not None else "GET")
    return json.load(urllib.request.urlopen(r, timeout=300))


def main():
    ver = sys.argv[1]
    dry = "--dry" in sys.argv
    only = None
    if "--only" in sys.argv:
        only = set(sys.argv[sys.argv.index("--only") + 1].split(","))
    token = open(os.environ["CF_TOKEN_FILE"]).read().strip()
    types = req("/game/version-types", token)
    versions = req("/game/versions", token)
    type_name = {t["id"]: t["slug"] for t in types}
    by_slug = {}
    for v in versions:
        by_slug.setdefault((type_name.get(v["gameVersionTypeID"], "?"), v["name"].lower()), v["id"])

    def mc_id(mc):
        p = mc.split(".")
        for (tslug, name), vid in by_slug.items():
            if name == mc and (tslug.startswith("minecraft-") or tslug.startswith("minecraft")):
                return vid
        return None

    def named(name):
        for (tslug, n), vid in by_slug.items():
            if n == name.lower():
                return vid
        return None

    loaders = {"fabric": named("Fabric"), "forge": named("Forge"), "neoforge": named("NeoForge")}
    env = [x for x in (named("Client"), named("Server")) if x]
    changelog = open(os.path.join(ROOT, "dist", f"changelog_{ver}.md"), encoding="utf-8").read()
    jars = sorted(f for f in os.listdir(os.path.join(ROOT, "dist", "jars")) if f.startswith(f"skywyrms-{ver}-") and f.endswith(".jar"))
    ok = 0
    for f in jars:
        t = f[len(f"skywyrms-{ver}-"):-4]
        if only and t not in only:
            continue
        loader, mc = t.split("-", 1)
        gv = [mc_id(mc), loaders[loader]] + env
        if None in gv[:2]:
            print("SKIP (no game version id)", f, gv)
            continue
        meta = {"changelog": changelog, "changelogType": "markdown", "displayName": f[:-4], "gameVersions": gv, "releaseType": "release"}
        if dry:
            print("DRY", f, gv)
            ok += 1
            continue
        b = uuid.uuid4().hex
        body = (f"--{b}\r\nContent-Disposition: form-data; name=\"metadata\"\r\n\r\n{json.dumps(meta)}\r\n"
                f"--{b}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{f}\"\r\nContent-Type: application/java-archive\r\n\r\n").encode()
        body += open(os.path.join(ROOT, "dist", "jars", f), "rb").read() + f"\r\n--{b}--\r\n".encode()
        for attempt in range(3):
            try:
                r = req(f"/projects/{PROJECT}/upload-file", token, body, {"Content-Type": f"multipart/form-data; boundary={b}"})
                print("OK", f, r.get("id"))
                ok += 1
                break
            except Exception as e:  # noqa: BLE001
                print("retry", f, e)
                time.sleep(5)
        time.sleep(1.5)
    print(f"{ok}/{len(jars) if not only else len(only)} uploaded{' (dry run)' if dry else ''}")


if __name__ == "__main__":
    main()
