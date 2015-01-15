#!/bin/bash
set -e

echo "Starting Bunker service..."

# -u to disable log output buffering
python -u /opt/bunker/entry.py
