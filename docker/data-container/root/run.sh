#!/bin/bash
set -e

echo "Creating deployment secret..."
python -c "import os; print os.urandom(16).encode('hex')" > /data/deployment_secret

echo "Exiting..."
