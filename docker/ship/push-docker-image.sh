#!/bin/bash
set +e

# Retry 'docker push $0' with exponential backoff.
# Needed as some docker registries are not reliably reachable.
# Will retry 6 times before failing.

RETRY=0
TIMEOUT=1
while true; do
    docker push $1
    if [ $? = 0 ]; then
        break
    elif [ ${RETRY} = 6 ]; then
        echo "ERROR: Retried too many times. I gave up."
        exit 22
    else
        echo "Retry #${RETRY} in ${TIMEOUT} seconds..."
        sleep ${TIMEOUT}
        TIMEOUT=$[TIMEOUT * 2]
        RETRY=$[RETRY + 1]
    fi
done
