#!/bin/bash
set -e

/container-scripts/copy-ca-cert /opt/repackaging

echo Starting up Repackaging...

# -u to disable output buffering
/container-scripts/restart-on-error python -u /opt/repackaging/api/main.py
