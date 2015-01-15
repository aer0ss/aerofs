#!/bin/bash
# Don't set -e. The script should keep going and try again later on error.

while true; do
    echo "Checking license validity..."
    VALID="$(curl -sS config.service:5434/is_license_valid)"
    # In case of errors, $VALID will be empty and no action should be made.
    if [ "${VALID}" = 0 ]; then
        echo "License is invalid."
        TARGET="$(curl -sS loader.service/v1/boot | grep '"target":' | awk '{$print $2}' | sed -e 's/"//')"
        if [ "${TARGET}" != maintenance ]; then
            echo "Current target is '${TARGET}'. Rebooting to maintenance..."
            curl -XPOST loader.service/v1/boot/current/current/maintenance
        else
            echo "Current target is maintenance. No rebooting required."
        fi
    else
        echo "License is or is assumed valid."
    fi

    # Sleep for one hour
    sleep 3600
done
