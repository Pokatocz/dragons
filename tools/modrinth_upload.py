#!/usr/bin/env python3
"""Upload the release jars of one version to the Modrinth project "Dragons" (id 2LXfrqfi).

    MODRINTH_TOKEN_FILE=/path/to/token python3 tools/modrinth_upload.py 1.2.9 [--dry] [--only fabric-1.21.1,...]

The token (a personal access token with the "Create versions" scope) is read from the file named by
MODRINTH_TOKEN_FILE, never from the command line, and never printed.
Each jar becomes one version: number "<ver>+<loader>-<mc>", name "Dragons <ver> (<Loader> <mc>)",
changelog dist/changelog_<ver>.md, release; Fabric versions require Fabric API.
"""
import json, os, sys, time, urllib.error, urllib.request, uuid

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT = "2LXfrqfi"
FABRIC_API = "P7dR8mSH"
API = "https://api.modrinth.com/v2"
UA = "Pokatocz/dragons-uploader (github.com/Pokatocz/dragons)"
LOADER_NAME = {"fabric": "Fabric", "forge": "Forge", "neoforge": "NeoForge"}


def main():
    ver = sys.argv[1]
    dry = "--dry" in sys.argv
    only = set(sys.argv[sys.argv.index("--only") + 1].split(",")) if "--only" in sys.argv else None
    token = None if dry else open(os.environ["MODRINTH_TOKEN_FILE"]).read().strip()
    known = {v["version"] for v in json.load(urllib.request.urlopen(
        urllib.request.Request(API + "/tag/game_version", headers={"User-Agent": UA}), timeout=60))}
    changelog = open(os.path.join(ROOT, "dist", f"changelog_{ver}.md"), encoding="utf-8").read()
    jars = sorted(f for f in os.listdir(os.path.join(ROOT, "dist", "jars"))
                  if f.startswith(f"skywyrms-{ver}-") and f.endswith(".jar"))
    ok = 0
    for f in jars:
        t = f[len(f"skywyrms-{ver}-"):-4]
        if only and t not in only:
            continue
        loader, mc = t.split("-", 1)
        if mc not in known:
            print("SKIP (unknown game version)", f)
            continue
        data = {
            "project_id": PROJECT,
            "name": f"Dragons {ver} ({LOADER_NAME[loader]} {mc})",
            "version_number": f"{ver}+{loader}-{mc}",
            "changelog": changelog,
            "dependencies": [{"project_id": FABRIC_API, "dependency_type": "required"}] if loader == "fabric" else [],
            "game_versions": [mc],
            "version_type": "release",
            "loaders": [loader],
            "featured": False,
            "status": "listed",
            "file_parts": ["file"],
            "primary_file": "file",
        }
        if dry:
            print("DRY", f, data["version_number"], data["loaders"], data["game_versions"], len(data["dependencies"]))
            ok += 1
            continue
        b = uuid.uuid4().hex
        body = (f"--{b}\r\nContent-Disposition: form-data; name=\"data\"\r\n\r\n{json.dumps(data)}\r\n"
                f"--{b}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{f}\"\r\n"
                f"Content-Type: application/java-archive\r\n\r\n").encode()
        body += open(os.path.join(ROOT, "dist", "jars", f), "rb").read() + f"\r\n--{b}--\r\n".encode()
        for attempt in range(3):
            try:
                r = urllib.request.Request(API + "/version", data=body, method="POST", headers={
                    "Authorization": token, "User-Agent": UA, "Content-Type": f"multipart/form-data; boundary={b}"})
                res = json.load(urllib.request.urlopen(r, timeout=300))
                print("OK", f, res.get("id"))
                ok += 1
                break
            except urllib.error.HTTPError as e:
                print("retry", f, e.code, e.read()[:300].decode("utf-8", "replace"))
                if e.code in (400, 401, 403):
                    break
                time.sleep(10)
            except Exception as e:  # noqa: BLE001
                print("retry", f, e)
                time.sleep(10)
        time.sleep(1.5)
    print(f"{ok}/{len(jars) if not only else len(only)} uploaded{' (dry run)' if dry else ''}")


if __name__ == "__main__":
    main()
