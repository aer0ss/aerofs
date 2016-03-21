#!/bin/bash
set -e

THIS_DIR="$(dirname "${BASH_SOURCE[0]}")"
CRANE_YML="$THIS_DIR/../ship-aerofs/sa-loader/root/crane.yml"
OUTPUT="$THIS_DIR/../sa-crane.yml"

# make sure that this matches the modifications done to the sa-loader image by
# modify-sa-loader.sh
cat "$CRANE_YML" | sed -e s/443:/444:/ -e s/loader:/sa-loader:/ > "$OUTPUT"

