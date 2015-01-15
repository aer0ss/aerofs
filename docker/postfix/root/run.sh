#!/bin/bash
set -e

# TODO (WW) run postfix or tail its logs in the foreground
echo "Starting postfix in the background..."
/etc/init.d/postfix start
sleep infinity
