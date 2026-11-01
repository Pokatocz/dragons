#!/usr/bin/env bash
# verify_all.sh <jar dir> <version> [targets...]: rebuilt jar vs published jar, decompiled-source diff per target
D=$1; V=$2; shift 2
for j in $D/skywyrms-$V-*.jar; do
  t=$(basename $j .jar); t=${t#skywyrms-$V-}
  [ $# -gt 0 ] && [[ ! " $* " =~ " $t " ]] && continue
  nice -n 10 python3 "$(dirname "$0")/verify.py" $j $t 2>&1 | tail -1
done
