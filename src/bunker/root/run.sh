#!/bin/bash
set -e

echo "Starting Bunker service..."

LOG_LEVEL="$(/container-scripts/get-config-property base.log.level)"

sed -e "s/{{ log_level }}/$LOG_LEVEL/" \
    /opt/bunker/bunker.ini.template > /opt/bunker/bunker.ini

# -u to disable log output buffering
/container-scripts/restart-on-error python -u /opt/bunker/entry.py
