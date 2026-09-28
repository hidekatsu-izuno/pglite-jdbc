#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export RAYON_NUM_THREADS=1
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx256m -XX:ActiveProcessorCount=1}"
runner=(nice -n 19)
if command -v taskset >/dev/null && command -v python3 >/dev/null; then
    cpu=$(python3 -c 'import os; print(min(os.sched_getaffinity(0)))')
    runner+=(taskset -c "$cpu")
fi
exec python3 scripts/run-with-memory-limit.py "${runner[@]}" mise x -- mvn -B -DforkCount=1 -DreuseForks=false \
    "-DargLine=-Xmx${PGLITE_TEST_HEAP:-384m} -Xss8m -XX:ActiveProcessorCount=1" \
    -Djunit.jupiter.execution.parallel.enabled=false \
    "-Dpglite.build.directory=$PWD/tmp/test-build" "$@" test
