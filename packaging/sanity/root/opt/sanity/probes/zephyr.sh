#!/bin/bash
set -e

# Note: we can't use Docker links to resolve Zephyr's address like we do for other probes because on
# Hosted Private Cloud Zephyr will be running on a separate machine so Docker links won't work.
# Querying the config server is the only way to get Zephyr's address.

ZEPHYR_ADDRESS=$(/container-scripts/get-config-property base.zephyr.address)

if [ -z "$ZEPHYR_ADDRESS" ]; then
    echo "Zephyr address not found. Is the config service down?"
    exit 1
fi

# Split the address in host and port
IFS=':' read -a TMP <<< $ZEPHYR_ADDRESS
ZEPHYR_HOST=${TMP[0]}
ZEPHYR_PORT=${TMP[1]}

/opt/sanity/probes/tools/port.sh $ZEPHYR_HOST $ZEPHYR_PORT
