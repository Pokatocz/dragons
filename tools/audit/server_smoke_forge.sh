#!/bin/bash
# tools/audit/server_smoke_forge.sh <forge|neoforge> <loader version> <mc> <mod jar>
# Installs a real dedicated Forge / NeoForge server, starts it with the mod, summons dragons and an egg, waits for the
# egg to hatch with nobody online and prints the errors of the log. The dedicated server is where client-only code crashes.
R=$(cd $(dirname $0)/../.. && pwd)
L=$1; V=$2; MC=$3; MOD=$4
S=$R/run/instances/srv-$L-$MC
JAVA=$(ls -d /opt/jdk/jdk-21*/bin/java | head -1)
pkill -f "srv-$L-$MC" 2>/dev/null; sleep 1
rm -rf $S; mkdir -p $S/mods $S/config; cd $S
if [ $L = neoforge ]; then URL="https://maven.neoforged.net/releases/net/neoforged/neoforge/$V/neoforge-$V-installer.jar"
else URL="https://maven.minecraftforge.net/net/minecraftforge/forge/$MC-$V/forge-$MC-$V-installer.jar"; fi
curl -sS -L -m 180 -A "Mozilla/5.0" -o installer.jar "$URL" || exit 1
$JAVA -jar installer.jar --installServer . > install.log 2>&1 || { echo "INSTALL FAILED"; tail -5 install.log; exit 1; }
cp $MOD mods/
echo eula=true > eula.txt
printf 'level-type=minecraft\\:flat\nonline-mode=false\nserver-port=25597\ngamemode=creative\ndifficulty=easy\nspawn-protection=0\ngenerate-structures=false\nview-distance=6\n' > server.properties
echo '{"eggHatchMinutes":0.1}' > config/skywyrms.json
ARGS=$(ls libraries/net/*/*forge*/*/unix_args.txt 2>/dev/null | head -1)
mkfifo in
if [ -n "$ARGS" ]; then ($JAVA -Xmx1500m @$ARGS nogui < in > server.log 2>&1) &
else ($JAVA -Xmx1500m -jar $(ls forge-*.jar | grep -v installer | head -1) nogui < in > server.log 2>&1) & fi
exec 3> in
say() { echo "$*" >&3; }
for i in $(seq 1 150); do grep -q 'Done (' server.log 2>/dev/null && break; grep -qE "Crash report|crash-reports" server.log 2>/dev/null && break; sleep 2; done
grep -q 'Done (' server.log || { echo "SERVER DID NOT START"; grep -aE "ERROR|Exception|Caused by|invalid dist" server.log | head -20; exec 3>&-; pkill -f "$S" ; exit 1; }
echo "server started: $(grep -o 'Done ([0-9.]*s)' server.log)"
for k in ember frost storm thorn void tide; do say "summon skywyrms:${k}_wyrm 0 -60 6"; done
say "summon skywyrms:dragon_egg 3 -60 3"
sleep 25
say "execute if entity @e[type=skywyrms:dragon_egg]"
say "execute as @e[type=skywyrms:ember_wyrm] run data get entity @s Age"
sleep 3
say "stop"; sleep 10; exec 3>&-
pkill -f "$S" 2>/dev/null
echo "--- commands"
grep -aE "Summoned new|Test passed|Test failed|has the following entity data|No entity was found|Unknown|Incorrect" server.log | sed -E 's/^\[[^]]*\] \[[^]]*\](: | \[[^]]*\]: )//' | sort | uniq -c | head -14
echo "--- errors and warnings"
grep -aE "/(ERROR|WARN|FATAL)\]|Exception|Caused by|invalid dist" server.log | grep -avE "Ambiguity between arguments|server.properties|Can't keep up|offline|authenticate|online-mode|To change this|While this makes|No key layers|ModConfigSpec|Configuration file|was corrected from|mixin|Mixin|assets URL|Assets URL|is not a valid|lowcodelanguage|javafml|minecraft/.*datafix|Unable to find spawn biome|removeErroring|PlayerList" | head -15
