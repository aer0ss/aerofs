#!/bin/bash

if [ $# = 0 ]; then
    echo "Usage: dk-reload container1 [container2 [...]]" >&2
    exit 11
fi

"$(dirname "${BASH_SOURCE[0]}")/dk-crane.sh" run --recreate -aall $@
