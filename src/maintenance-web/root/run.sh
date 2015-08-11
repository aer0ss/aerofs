#!/bin/bash
set -e

echo "Starting maintenance service..."

# -u to disable log output buffering
/container-scripts/restart-on-error python -u /opt/maintenance-web/entry.py
