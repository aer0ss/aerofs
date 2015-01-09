#!/bin/bash

CW=$(dirname "${BASH_SOURCE[0]}")/../crane-wrapper
if [[ ! -f $CW ]]; then
    echo "The crane-wrapper script (expected at $CW) was not found."
fi

# Run crane from any folder
alias crane="$CW"

# `relaunch` with a container name, or no arguments to relaunch the entire system
# (will destroy persistent data).
alias relaunch="$CW run --recreate -aall" 
