#!/bin/bash
set -e

PROP_FILE=/opt/config/properties/external.properties
if [ ! -f $PROP_FILE ]; then
    echo Creating $PROP_FILE...
    cat /external.properties.default /external.properties.docker.default > $PROP_FILE
fi

# -u to disable console print caching
python -u /opt/config/entry.py
