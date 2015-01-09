#!/bin/bash
set -e

# Limit pypy max heap size; it has no business being huge forever
export PYPY_GC_MAX="1.0GB"

# Set a high ulimit for fds to allow a large # of concurrent connections
ulimit -S -n 1024000
ulimit -H -n 1024000

# -u to disable log output buffering
/pythonenv/bin/python -u /opt/charlie/charles.py
