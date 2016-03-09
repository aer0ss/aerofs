#!/bin/bash
set -e

echo "Starting Bunker service..."

LOG_LEVEL="$(/container-scripts/get-config-property base.log.level)"

sed -e "s/{{ log_level }}/$LOG_LEVEL/" \
    /opt/bunker/bunker.ini.template > /opt/bunker/bunker.ini

# run the celery worker
celery worker -D -A web.celery --logfile=/var/log/celery.log --pidfile=/var/run/celery.pid

# -u to disable log output buffering
/container-scripts/restart-on-error python -u /opt/bunker/entry.py
