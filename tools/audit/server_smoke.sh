#!/bin/bash
# tools/audit/server_smoke.sh <mc> <fabric loader> <fabric api jar> <mod jar> [join]
# A real dedicated Fabric server with the mod: dragons and an egg ticking with nobody online, then (join) a real client
# connects, gets a tamed, saddled dragon and is put on it. Prints the errors of both logs.
R=$(cd $(dirname $0)/../.. && pwd)
MC=$1; LOADER=$2; API=$3; MOD=$4; JOIN=$5
S=$R/run/instances/srv-fabric-$MC
JAVA=$(ls -d /opt/jdk/jdk-21*/bin/java | head -1)
[ "${MC%%.*}" = "26" ] && JAVA=$(ls -d /opt/jdk/jdk-25*/bin/java | head -1)
pkill -f "fabric-server.jar" 2>/dev/null; sleep 1
rm -rf $S; mkdir -p $S/mods $S/config
curl -sS -m 90 -o $S/fabric-server.jar "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/1.1.0/server/jar" || exit 1
[ -f $R/run/templates/$MC/server/server.jar ] && cp $R/run/templates/$MC/server/server.jar $S/server.jar
cp $API $MOD $S/mods/
echo eula=true > $S/eula.txt
printf 'level-type=minecraft\\:flat\nonline-mode=false\nserver-port=25598\ngamemode=creative\ndifficulty=easy\nspawn-protection=0\ngenerate-structures=false\nview-distance=6\nmotd=skywyrms smoke\n' > $S/server.properties
echo '{"eggHatchMinutes":0.1}' > $S/config/skywyrms.json
mkfifo $S/in
(cd $S && exec $JAVA -Xmx1500m -jar fabric-server.jar nogui < in > server.log 2>&1) &
exec 3> $S/in
say() { echo "$*" >&3; }
for i in $(seq 1 150); do grep -q 'Done (' $S/server.log 2>/dev/null && break; sleep 2; done
grep -q 'Done (' $S/server.log || { echo "SERVER DID NOT START"; tail -30 $S/server.log; exit 1; }
echo "server started: $(grep -o 'Done ([0-9.]*s)' $S/server.log)"
for k in ember frost storm thorn void tide; do say "summon skywyrms:${k}_wyrm 0 -60 6"; done
say "summon skywyrms:dragon_egg 3 -60 3"
sleep 20
say "execute if entity @e[type=skywyrms:dragon_egg]"
say "execute if entity @e[type=#minecraft:can_breathe_under_water,limit=0]"
say "execute as @e[type=skywyrms:ember_wyrm] run data get entity @s Age"
sleep 3
if [ -n "$JOIN" ]; then
  python3 $R/run/launch.py client --loader fabric --mc $MC --mods $MOD --instance cl-srv-fabric-$MC --fresh --world none --join localhost:25598 --wait-file never.txt --timeout 200 > $S/client-run.json 2>&1 &
  for i in $(seq 1 90); do grep -q 'Tester joined the game' $S/server.log && break; sleep 2; done
  grep -q 'Tester joined the game' $S/server.log && echo "client joined" || echo "CLIENT DID NOT JOIN"
  sleep 5
  say "op Tester"; say "kill @e[type=!minecraft:player]"; sleep 1
  say "tp Tester 0 -60 0 0 0"
  say "summon skywyrms:storm_wyrm 0 -60 6 {Saddled:1b}"
  say "summon skywyrms:thorn_wyrm 6 -60 6"
  sleep 2
  say "data modify entity @e[type=skywyrms:storm_wyrm,limit=1] Owner set from entity Tester UUID"
  say "ride Tester mount @e[type=skywyrms:storm_wyrm,limit=1]"
  sleep 15
  say "data get entity Tester RootVehicle.Entity.id"
  say "data get entity @e[type=skywyrms:storm_wyrm,limit=1] Saddled"
  sleep 15
fi
say "stop"
sleep 8
exec 3>&-
pkill -f "cl-srv-fabric-$MC" 2>/dev/null; pkill -f "fabric-server.jar" 2>/dev/null
echo "--- server log: commands"
grep -aE "Summoned new|Test passed|Test failed|has the following entity data|Started riding|Modified entity data|No entity was found|Unknown|Incorrect" $S/server.log | sed -E 's/^\[[0-9:]+\] \[[^]]*\]: //' | sort | uniq -c | head -20
echo "--- server log: errors and warnings"
grep -aE "/(ERROR|WARN|FATAL)\]|Exception|Caused by|\tat " $S/server.log | grep -avE "Ambiguity between arguments|server.properties|Can't keep up|offline/insecure|will make no attempt to authenticate|use 'online-mode'|To change this, set|While this makes the game possible|SERVER IS RUNNING IN OFFLINE" | head -20
echo "--- client log: errors and warnings"
C=$R/run/instances/cl-srv-fabric-$MC/logs/latest.log
[ -f $C ] && grep -aE "/(ERROR|WARN|FATAL)\]|Exception|Caused by" $C | grep -avE "authlib|realms|Realms|profile|Narrator|narrator|OpenAL|ALSA|SoundSystem|Sound|texture atlas|401|signature|session|telemetry|Ambiguity|oshi|GLX|glfw|Shader" | head -20
[ -f $C ] && grep -acE "skywyrms" $C
