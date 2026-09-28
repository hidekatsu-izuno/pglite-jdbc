#!/usr/bin/env python3
"""Run a test command, stopping its process group before it exhausts WSL RAM."""
import os
import pathlib
import signal
import subprocess
import sys

limit = int(os.environ.get('PGLITE_TEST_RSS_LIMIT_MIB', '3072')) * 1024 * 1024
page_size = os.sysconf('SC_PAGE_SIZE')
process = subprocess.Popen(sys.argv[1:], start_new_session=True)
peak = 0


def group_rss():
    total = 0
    for path in pathlib.Path('/proc').glob('[0-9]*/stat'):
        try:
            fields = path.read_text().rsplit(')', 1)[1].split()
            if int(fields[2]) == process.pid:
                total += int(fields[21]) * page_size
        except (OSError, ValueError, IndexError):
            pass
    return total


def stop():
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        pass
    # Also stop descendants that survive the Maven parent.
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass


try:
    while True:
        peak = max(peak, group_rss())
        if peak > limit:
            print(f'Test memory limit exceeded: {peak // 1048576} MiB > {limit // 1048576} MiB', file=sys.stderr)
            stop()
            sys.exit(1)
        try:
            result = process.wait(timeout=1)
            sys.exit(result if result >= 0 else 128 - result)
        except subprocess.TimeoutExpired:
            pass
except KeyboardInterrupt:
    stop()
    sys.exit(130)
finally:
    print(f'[test-runner] Peak process-group RSS: {peak // 1048576} MiB', file=sys.stderr)
