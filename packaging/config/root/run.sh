#!/bin/bash
set -e

CONFIG_DIR=/opt/config
PROP_FILE=$CONFIG_DIR/properties/external.properties
if [ ! -f $PROP_FILE ]; then
    echo Creating $PROP_FILE...
    cat $CONFIG_DIR/external.properties.default $CONFIG_DIR/external.properties.docker.default > $PROP_FILE
fi

# Add default properties specific to the dockerized appliance if they aren't present.
for i in $(cat $CONFIG_DIR/external.properties.docker.default); do
    KEY=$(echo "$i" | sed -e "s/=.*//")
    if [ -z "$(grep "^${KEY}=" ${PROP_FILE})" ]; then
        echo Adding new default key: $KEY
        echo "${i}" >> ${PROP_FILE}
    fi
done

# The string replacement is to support restoring from legacy appliances.
sed -i "s/email_host=localhost/email_host=postfix.service/" ${PROP_FILE}

# -u to disable console print caching
/container-scripts/restart-on-error python -u /opt/config/entry.py
