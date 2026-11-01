#!/usr/bin/env python3
"""Headless Minecraft client launcher for automated in-game tests of the Skywyrms jars.

    python3 run/launch.py client --loader fabric --mc 1.21.11 --mods a.jar,b.jar --instance NAME --fresh \
        [--jvm "-Dskywyrms.autotest=true"] [--wait-file autotest_done.txt] [--wait-log REGEX] [--settle 3] \
        [--timeout 900] [--world flat|none]

Install: portablemc (versions, libraries, assets, Fabric/Forge/NeoForge) under run/mcroot.
World: a superflat world made once per version with that version's vanilla dedicated server (run/templates),
       patched to creative + cheats, joined with Quick Play.
Display: own Xvfb; software OpenGL (Mesa llvmpipe). Lines "[autotest] SHOT <name>" in the game log make the
         launcher grab the screen into <instance>/screenshots/<name>.png.
Last stdout line: one JSON object (ok, exit_reason, elapsed, log, crash_report, ...).
"""
import argparse, json, os, re, shutil, signal, subprocess, sys, threading, time, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN = os.path.join(ROOT, "run")
MCROOT = os.path.join(RUN, "mcroot")
TEMPLATES = os.path.join(RUN, "templates")
INSTANCES = os.path.join(RUN, "instances")
CACHE = os.path.join(RUN, "cache")
JAVA21 = "/opt/jdk/jdk-21.0.13+9/bin/java"
JAVA25 = "/opt/jdk/jdk-25.0.5+8/bin/java"
FABRIC_LOADER = "0.19.5"
UA = {"User-Agent": "Mozilla/5.0"}
sys.path.insert(0, os.path.join(ROOT, "build"))


def log(*a):
    print(*a, file=sys.stderr, flush=True)


def mcnum(v):
    p = [int(x) for x in v.split(".")] + [0, 0]
    return p[0] * 10000 + p[1] * 100 + p[2]


def java_for(mc):
    return JAVA25 if mcnum(mc) >= 260000 else JAVA21


def fetch(url, path):
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=180).read()
    open(path + ".part", "wb").write(data)
    os.replace(path + ".part", path)
    return path


def loader_spec(loader, mc):
    if loader == "fabric":
        return f"fabric:{mc}:{FABRIC_LOADER}"
    import build  # same loader version the jar was compiled against
    v = build.loader_version(loader, mc)
    return f"forge::{mc}-{v}" if loader == "forge" else f"neoforge::{v}"


def install(loader, mc):
    spec = loader_spec(loader, mc)
    mark = os.path.join(MCROOT, ".installed", spec.replace(":", "_"))
    if not os.path.exists(mark):
        log(f"installing {spec} ...")
        r = subprocess.run(["portablemc", "--main-dir", MCROOT, "start", spec, "--dry", "--jvm", java_for(mc)],
                           capture_output=True, text=True)
        if r.returncode != 0:
            raise RuntimeError("install failed: " + (r.stdout + r.stderr)[-3000:])
        os.makedirs(os.path.dirname(mark), exist_ok=True)
        open(mark, "w").write("ok")
    return spec


def fabric_api_jar(mc):
    meta = json.load(open(os.path.join(ROOT, "build", "meta", f"fabric-{mc}", "fabric.mod.json")))
    v = meta["depends"]["fabric-api"].lstrip(">=") + "+" + mc
    return fetch(f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{v}/fabric-api-{v}.jar",
                 os.path.join(CACHE, "fabric-api", f"fabric-api-{v}.jar"))


# ------------------------------------------------------------------ world template
def template(mc):
    world = os.path.join(TEMPLATES, mc, "world")
    if os.path.exists(os.path.join(world, "level.dat")):
        return world
    log(f"generating flat world for {mc} ...")
    man = json.load(urllib.request.urlopen("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
    vj = json.load(urllib.request.urlopen(next(v["url"] for v in man["versions"] if v["id"] == mc)))
    sdir = os.path.join(TEMPLATES, mc, "server")
    os.makedirs(sdir, exist_ok=True)
    jar = fetch(vj["downloads"]["server"]["url"], os.path.join(sdir, "server.jar"))
    open(os.path.join(sdir, "eula.txt"), "w").write("eula=true\n")
    open(os.path.join(sdir, "server.properties"), "w").write(
        "level-type=minecraft\\:flat\ngamemode=creative\ngenerate-structures=false\nspawn-protection=0\nonline-mode=false\n"
        "spawn-monsters=false\nspawn-npcs=false\nspawn-animals=false\ndifficulty=peaceful\nserver-port=25599\nlevel-name=world\n")
    p = subprocess.Popen([java_for(mc), "-Xmx1g", "-jar", jar, "nogui"], cwd=sdir, stdin=subprocess.PIPE,
                         stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    t0 = time.time()
    for line in p.stdout:
        if "Done (" in line:
            p.stdin.write("stop\n")
            p.stdin.flush()
        if time.time() - t0 > 600:
            p.kill()
            break
    p.wait()
    src = os.path.join(sdir, "world")
    import nbtlib
    lv = nbtlib.load(os.path.join(src, "level.dat"))
    data = lv["Data"] if "Data" in lv else lv[""]["Data"]
    data["allowCommands"] = nbtlib.Byte(1)
    data["GameType"] = nbtlib.Int(1)
    data["Difficulty"] = nbtlib.Byte(0) if "Difficulty" in data else nbtlib.Byte(0)
    if "GameRules" in data:
        for k, v in (("doDaylightCycle", "false"), ("doWeatherCycle", "false"), ("doMobSpawning", "false")):
            data["GameRules"][k] = nbtlib.String(v)
    lv.save()
    shutil.rmtree(world, ignore_errors=True)
    shutil.copytree(src, world)
    for junk in ("session.lock",):
        try:
            os.remove(os.path.join(world, junk))
        except OSError:
            pass
    shutil.rmtree(sdir, ignore_errors=True)
    return world


OPTIONS = """pauseOnLostFocus:false
onboardAccessibility:false
narrator:0
renderDistance:6
simulationDistance:6
maxFps:60
fullscreen:false
tutorialStep:none
joinedFirstServer:true
skipMultiplayerWarning:true
soundCategory_master:0.0
guiScale:2
fov:0.0
renderClouds:"false"
"""


# ------------------------------------------------------------------ client
def free_display():
    for n in range(90, 140):
        if not os.path.exists(f"/tmp/.X{n}-lock"):
            return n
    raise RuntimeError("no free X display")


def grab(display, out):
    os.makedirs(os.path.dirname(out), exist_ok=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "x11grab", "-video_size", "1280x720", "-i", f":{display}",
                    "-frames:v", "1", out], capture_output=True)


def client(a):
    t_start = time.time()
    spec = install(a.loader, a.mc)
    inst = os.path.join(INSTANCES, a.instance)
    if a.fresh:
        shutil.rmtree(inst, ignore_errors=True)
    os.makedirs(os.path.join(inst, "mods"), exist_ok=True)
    for f in os.listdir(os.path.join(inst, "mods")):
        os.remove(os.path.join(inst, "mods", f))
    mods = [m for m in (a.mods or "").split(",") if m]
    if a.loader == "fabric":
        mods.append(fabric_api_jar(a.mc))
    for m in mods:
        shutil.copy(m, os.path.join(inst, "mods", os.path.basename(m)))
    opt = os.path.join(inst, "options.txt")
    keep = {}
    if os.path.exists(opt):
        for line in open(opt):
            if ":" in line:
                k, v = line.rstrip("\n").split(":", 1)
                keep[k] = v
    for line in OPTIONS.strip().split("\n"):
        k, v = line.split(":", 1)
        keep[k] = v
    open(opt, "w").write("".join(f"{k}:{v}\n" for k, v in keep.items()))
    if a.world == "flat" and not os.path.exists(os.path.join(inst, "saves", "world", "level.dat")):
        shutil.copytree(template(a.mc), os.path.join(inst, "saves", "world"), dirs_exist_ok=True)
    if a.wait_file:
        try:
            os.remove(os.path.join(inst, a.wait_file))
        except OSError:
            pass
    shutil.rmtree(os.path.join(inst, "crash-reports"), ignore_errors=True)
    disp = free_display()
    xargs = os.environ.get("XVFB_ARGS", "-screen 0 1280x720x24 +extension GLX +render -noreset").split()
    xvfb = subprocess.Popen(["Xvfb", f":{disp}"] + xargs + ["-nolisten", "tcp"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    env = dict(os.environ, DISPLAY=f":{disp}", SDL_VIDEODRIVER="x11", SDL_VIDEO_FORCE_EGL="1", SDL_VIDEO_X11_FORCE_EGL="1", XDG_RUNTIME_DIR="/tmp/xdg-runtime", LIBGL_ALWAYS_SOFTWARE="1", MESA_GL_VERSION_OVERRIDE="4.5",
               MESA_GLSL_VERSION_OVERRIDE="450", __GL_SYNC_TO_VBLANK="0", vblank_mode="0")
    jvm = ["-Xmx2200m", "-Djava.awt.headless=true"] + (a.jvm.split() if a.jvm else [])
    cmd = ["portablemc", "--main-dir", MCROOT, "start", spec, "--mc-dir", inst, "-u", "Tester", "--jvm", java_for(a.mc),
           "--fetch-exclude-all", "--resolution", "1280x720", "--jvm-arg=" + ",".join(jvm)]
    if a.world == "flat":
        cmd += ["--join-world", "world"]
    open(os.path.join(inst, "launch-command.txt"), "w").write(" ".join(cmd) + "\n")
    logf = open(os.path.join(inst, "client.log"), "w")
    game = subprocess.Popen(cmd, cwd=inst, env=env, stdout=logf, stderr=subprocess.STDOUT, start_new_session=True)
    result = {"kind": "client", "loader": a.loader, "mc": a.mc, "instance": a.instance, "ok": False, "exit_reason": None,
              "log": os.path.join(inst, "client.log"), "crash_report": None, "game_dir": inst, "mods": [os.path.basename(m) for m in mods]}
    shots = set()
    pos = 0
    wait_re = re.compile(a.wait_log) if a.wait_log else None
    fail_re = re.compile(a.fail_log) if a.fail_log else None
    while True:
        time.sleep(0.5)
        try:
            with open(os.path.join(inst, "client.log"), encoding="utf-8", errors="replace") as f:
                f.seek(pos)
                chunk = f.read()
                pos = f.tell()
        except OSError:
            chunk = ""
        for line in chunk.split("\n"):
            m = re.search(r"\[autotest\] SHOT (\S+)", line)
            if m and m.group(1) not in shots:
                shots.add(m.group(1))
                grab(disp, os.path.join(inst, "screenshots", m.group(1) + ".png"))
            if wait_re and wait_re.search(line):
                result["exit_reason"] = "wait-log"
            if fail_re and fail_re.search(line):
                result["exit_reason"] = "fail-log"
        if a.wait_file and os.path.exists(os.path.join(inst, a.wait_file)):
            result["exit_reason"] = "wait-file"
        crash = os.path.join(inst, "crash-reports")
        if os.path.isdir(crash) and os.listdir(crash):
            result["exit_reason"] = "crash-report"
            result["crash_report"] = os.path.join(crash, sorted(os.listdir(crash))[-1])
        if game.poll() is not None and not result["exit_reason"]:
            result["exit_reason"] = "exited"
            result["exit_code"] = game.returncode
        if time.time() - t_start > a.timeout and not result["exit_reason"]:
            result["exit_reason"] = "timeout"
        if result["exit_reason"]:
            break
    result["ok"] = result["exit_reason"] in ("wait-file", "wait-log")
    if result["ok"] and a.settle:
        time.sleep(a.settle)
    if a.shot:
        grab(disp, a.shot)
    try:
        os.killpg(game.pid, signal.SIGTERM)
        game.wait(20)
    except Exception:  # noqa: BLE001
        try:
            os.killpg(game.pid, signal.SIGKILL)
        except Exception:  # noqa: BLE001
            pass
    xvfb.terminate()
    result["elapsed"] = round(time.time() - t_start, 1)
    print(json.dumps(result))
    return 0 if result["ok"] else 1


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("client")
    c.add_argument("--loader", required=True, choices=["fabric", "forge", "neoforge"])
    c.add_argument("--mc", required=True)
    c.add_argument("--instance", required=True)
    c.add_argument("--mods", default="")
    c.add_argument("--world", default="flat", choices=["flat", "none"])
    c.add_argument("--jvm", default="")
    c.add_argument("--timeout", type=float, default=900)
    c.add_argument("--wait-file")
    c.add_argument("--wait-log")
    c.add_argument("--fail-log")
    c.add_argument("--settle", type=float, default=0)
    c.add_argument("--shot")
    c.add_argument("--fresh", action="store_true")
    i = sub.add_parser("install")
    i.add_argument("--loader", required=True)
    i.add_argument("--mc", required=True)
    w = sub.add_parser("template")
    w.add_argument("--mc", required=True)
    a = ap.parse_args()
    if a.cmd == "client":
        sys.exit(client(a))
    if a.cmd == "install":
        print(install(a.loader, a.mc))
    if a.cmd == "template":
        print(template(a.mc))


if __name__ == "__main__":
    main()
