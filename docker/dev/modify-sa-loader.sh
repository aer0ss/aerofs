#!/bin/bash
set -e

# the storage agent's crane.yml is modified to be able to run alongside the aerofs appliance
# make the same changes to the sa-loader image for dev environments to stay consistent

LOADER="aerofs/sa-loader"
echo "Modifying ${LOADER} ..."
true && {
    TMP="$(mktemp -d -t XXXXXX)"
    # keep these modifications in sync with gen-sa-crane-yml.sh
    cat > "${TMP}/Dockerfile" <<END
FROM ${LOADER}
RUN  sed -i \
        -e s/443:/444:/ -e s/loader:/sa-loader:/ \
        /crane.yml
END
    docker build -t ${LOADER} "${TMP}"
    rm -rf "${TMP}"
}
