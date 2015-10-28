#!/bin/bash
set -e

if [ ! -f /data/deployment_secret ]
then
    echo "Creating deployment secret..."
    python -c "import os; print os.urandom(16).encode('hex')" > /data/deployment_secret
else
    echo "Deployment secret already created. No-op."
fi

echo "Exiting..."
