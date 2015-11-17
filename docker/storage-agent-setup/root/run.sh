#!/bin/bash
set -e

echo "Setting up storage-agent..."

python /opt/storage-agent-setup/setup_storage_agent.py -f /aerofs/unattended-setup.properties
curl -X POST loader.service/v1/boot/default
