#!/usr/bin/env bash
# [SCEN=main|spawn|owner|breedstay] test/batch.sh "<loader>:<mc>[:extra.jar+extra.jar] ..." [out.log]
# Runs the AUTOTEST jar of each target (dist/test-jars) in the headless client, optionally with extra mods
# (paths relative to test/mods/), and appends one block per target to the log. Screenshots and logs are kept in
# test/results/<instance>/, the instance itself is deleted (disk).
R=$(cd "$(dirname "$0")/.." && pwd)
OUT=${2:-$R/test/batch.log}
SCEN=${SCEN:-main}
n=0
for t in $1; do
  n=$((n+1))
  L=$(echo "$t" | cut -d: -f1); V=$(echo "$t" | cut -d: -f2); X=$(echo "$t" | cut -s -d: -f3)
  JAR=$(ls $R/dist/test-jars/skywyrms-*-$L-$V.jar 2>/dev/null | tail -1)
  MODS="$JAR"; for x in $(echo "$X" | tr '+' ' '); do MODS="$MODS,$R/test/mods/$x"; done
  INST="at-$SCEN-$L-$V${X:+-x$n}"; D=$R/run/instances/$INST
  echo "=== $SCEN $L $V ${X:++ $(echo $X | tr '+' ' ')} $(date +%H:%M:%S)" >> $OUT
  RES=$(timeout 1500 python3 $R/run/launch.py client --loader "$L" --mc "$V" --mods "$MODS" --instance "$INST" --fresh \
        "--jvm=-Dskywyrms.autotest=$SCEN" --wait-file autotest_done.txt --settle 2 --timeout 1300 2>/dev/null | tail -1)
  echo "  $(echo "$RES" | grep -o '"exit_reason": "[^"]*"\|"crash_report": "[^"]*"' | tr '\n' ' ')" >> $OUT
  if [ -f $D/autotest_done.txt ]; then
    echo "  result: $(head -1 $D/autotest_done.txt)  passed: $(grep -c ': true' $D/autotest_done.txt)  failed: $(grep -E '^[a-z_]+: false' $D/autotest_done.txt | cut -d' ' -f1 | tr '\n' ' ')" >> $OUT
  fi
  grep -a -E "Exception|Caused by|FATAL" $D/client.log 2>/dev/null | grep -a -v -i "401\|realms\|authlib\|yggdrasil\|profile\|telemetry\|OpenAL\|SocketTimeout\|narrator\|UnknownHost" | head -3 | cut -c1-200 | sed 's/^/  ! /' >> $OUT
  mkdir -p $R/test/results/$INST
  cp -r $D/screenshots $D/autotest_done.txt $D/client.log $D/logs/latest.log $R/test/results/$INST/ 2>/dev/null
  [ -d $D/crash-reports ] && cp -r $D/crash-reports $R/test/results/$INST/
  rm -rf "$D"
done
echo "=== BATCH DONE $(date +%H:%M:%S)" >> $OUT
