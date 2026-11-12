#!/bin/bash
# tools/audit/forge_kill_check.sh <forge version> <mc> [mod jar]   - does killing a vanilla pig throw on a dedicated Forge server?
R=$(cd $(dirname $0)/../.. && pwd); V=$1; MC=$2; MOD=$3
S=$R/run/instances/srv-forge-$MC-kill; JAVA=$(ls -d /opt/jdk/jdk-21*/bin/java | head -1)
pkill -f "srv-forge-$MC-kill" 2>/dev/null; sleep 1
if [ ! -f $S/installed ]; then
  rm -rf $S; mkdir -p $S; cd $S
  curl -sS -L -m 180 -A "Mozilla/5.0" -o installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/$MC-$V/forge-$MC-$V-installer.jar" || exit 1
  $JAVA -jar installer.jar --installServer . > install.log 2>&1 || { echo "INSTALL FAILED"; tail -5 install.log; exit 1; }
  touch installed
fi
cd $S; rm -rf mods world logs config server.log in; mkdir -p mods; [ -n "$MOD" ] && cp $MOD mods/
echo eula=true > eula.txt
printf 'level-type=minecraft\\:flat\nonline-mode=false\nserver-port=25596\ndifficulty=easy\nspawn-protection=0\ngenerate-structures=false\n' > server.properties
ARGS=$(ls libraries/net/*/*forge*/*/unix_args.txt 2>/dev/null | head -1)
mkfifo in
if [ -n "$ARGS" ]; then ($JAVA -Xmx1500m @$ARGS nogui < in > server.log 2>&1) & else ($JAVA -Xmx1500m -jar $(ls forge-*.jar | grep -v installer | head -1) nogui < in > server.log 2>&1) & fi
exec 3> in
for i in $(seq 1 150); do grep -q 'Done (' server.log 2>/dev/null && break; sleep 2; done
echo "summon minecraft:pig 0 -60 0" >&3; sleep 2; echo "kill @e[type=minecraft:pig]" >&3; sleep 3
[ -n "$MOD" ] && { echo "summon skywyrms:ember_wyrm 0 -60 4" >&3; sleep 2; echo "kill @e[type=skywyrms:ember_wyrm]" >&3; sleep 3; }
echo "stop" >&3; sleep 8; exec 3>&-; pkill -f "srv-forge-$MC-kill" 2>/dev/null
echo "mods: ${MOD:+$(basename $MOD)}${MOD:-none}"
grep -aE "Summoned new|Killed |unexpected error" server.log | sed -E 's/^\[[^]]*\] \[[^]]*\](: | \[[^]]*\]: )//'
