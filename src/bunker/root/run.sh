#!/bin/bash
set -e

echo "Starting Bunker service..."

# -u to disable log output buffering
/container-scripts/restart-on-error python -u /opt/bunker/entry.py
