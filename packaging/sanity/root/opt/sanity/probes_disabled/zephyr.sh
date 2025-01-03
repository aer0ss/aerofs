#!/bin/bash
set -e

###################################################
# FIXME (GS): This probe has been disabled because some of our customers have weird networking
# configurations where the box can't reach itself via its external IP address. This isn't a
# problem for other services because we use Docker links instead of the external IP to reach
# them - see comment below.
###################################################

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
