#!/bin/bash
# test/lan.sh <loader> <mc> [nomod]  -  host opens a world with dragons to LAN, a second client joins it.
# "nomod" starts the joining client without the mod (to see what Minecraft says then).
R=$(cd $(dirname $0)/.. && pwd); L=$1; MC=$2; NOMOD=$3
JAR=$R/dist/test-jars/skywyrms-1.2.9-$L-$MC.jar
EXTRA=""; [ "$L" = "forge" ] && [ "$MC" = "1.21.1" ] && EXTRA=" -Dskywyrms.autotest.reload=1"
H=lanhost-$L-$MC; J=lanjoin-$L-$MC
SIG=/tmp/lan_signal_${L}_$MC; rm -f $SIG
rm -rf $R/run/instances/$H $R/run/instances/$J
python3 $R/run/launch.py client --loader $L --mc $MC --mods $JAR --instance $H --fresh --world flat "--jvm=-Dskywyrms.autotest=lanhost -Dskywyrms.autotest.lansignal=$SIG$EXTRA" --wait-file autotest_done.txt --timeout 900 > /tmp/lan_host_${L}_$MC.json 2>/dev/null &
HP=$!
for i in $(seq 1 300); do cat $R/run/instances/$H/client.log $R/run/instances/$H/logs/latest.log 2>/dev/null | grep -q "LAN OPEN" && break; sleep 2; done
cat $R/run/instances/$H/client.log $R/run/instances/$H/logs/latest.log 2>/dev/null | grep -q "LAN OPEN" || { echo "HOST DID NOT OPEN LAN"; }
JMODS=$JAR; [ "$NOMOD" = "nomod" ] && JMODS=""
MC_USER=Tester2 python3 $R/run/launch.py client --loader $L --mc $MC --mods "$JMODS" --instance $J --fresh --world none --join localhost:25601 "--jvm=-Dskywyrms.autotest=lanjoin -Dskywyrms.autotest.lansignal=$SIG" --wait-file autotest_done.txt --timeout 600 > /tmp/lan_join_${L}_$MC.json 2>/dev/null
wait $HP
echo "== host:  $(head -1 $R/run/instances/$H/autotest_done.txt 2>/dev/null)"; sed 1d $R/run/instances/$H/autotest_done.txt 2>/dev/null | grep -i lan
echo "== join:  $(head -1 $R/run/instances/$J/autotest_done.txt 2>/dev/null)"; sed 1d $R/run/instances/$J/autotest_done.txt 2>/dev/null
echo "== disconnect lines:"; grep -ahiE "disconnect|lost connection|kicked|left the game" $R/run/instances/$H/logs/latest.log $R/run/instances/$J/logs/latest.log 2>/dev/null | grep -v "autotest" | tail -6
echo "== errors (skywyrms / exceptions):"; grep -ahE "Exception|Caused by|/ERROR\]"  $R/run/instances/$H/logs/latest.log $R/run/instances/$J/logs/latest.log 2>/dev/null | grep -avE "authlib|Realms|realms|profile|Narrator|OpenAL|ALSA|Sound|telemetry|signature|session|oshi|GLX|glfw|Shader|401|Failed to fetch|YggdrasilServicesKeyInfo|unauthorized|No data fixer registered" | head -12
ls $R/run/instances/$H/crash-reports $R/run/instances/$J/crash-reports 2>/dev/null
