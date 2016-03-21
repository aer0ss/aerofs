#!/bin/bash
set -e

echo "starting setup server..."
cd /opt/storage-agent-setup
/container-scripts/restart-on-error python setup_server.py
