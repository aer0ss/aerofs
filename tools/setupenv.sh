#!/bin/bash

##
# This script is meant to be sourced so that all environment tools are
# initialized. This usually a one time setup script.
##

SCRIPT="$BASH_SOURCE"

# Check if path is relative
case "$SCRIPT" in
    /*)
        BASE="`dirname $SCRIPT`"
        ;;
    *)
        BASE="`pwd`/`dirname $SCRIPT`"
        ;;
esac

BASE="${BASE}/env"

# At this point, base is the tools/env directory

# Verify that we are currently in the AeroFS git repository
git rev-parse --show-toplevel > /dev/null 2>&1
if [ "$?" != "0" ]; then
    echo "This script must be run in the AeroFS git repository" 1>&2
    return 1
fi

# All setup happens below

source "${BASE}/git-alias-setup.sh"
source "${BASE}/git-hook-setup.sh"
source "${BASE}/populate"
