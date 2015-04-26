#!/bin/bash
set -e
#
# Touch-ups specific to CI and test environments, run in the appliance VM or developers' host computer.
#
# Please keep the amount of touch-ups small to minimize discrepancy between CI and produdction.
#
# The script should be idempotent to support repetitive runs in test environments.
#

# Figure out registry and version
SPECIMEN=aerofs/nginx
LINE="$(docker images | grep ${SPECIMEN})"
[[ $(echo "${LINE}" | wc -l) -eq 1 ]] || {
    echo "ERROR: there are more than one ${SPECIMEN} Docker image. Please remove unused ones."
    exit 22
}
REGISTRY="$(echo "${LINE}" | awk '{print $1}' | sed -e "s,${SPECIMEN}$,,")"
TAG="$(echo "${LINE}" | awk '{print $2}')"

# Enable disabled services in Nginx
NGINX="${REGISTRY}aerofs/nginx:${TAG}"
echo "Modifying ${NGINX} ..."
[[ "$(docker run "${NGINX}" ls '/etc/nginx/sites-disabled')" ]] && {
    TMP="$(mktemp -d -t XXXXXX)"
    cat > "${TMP}/Dockerfile" <<END
FROM ${NGINX}
RUN  mv /etc/nginx/sites-disabled/* /etc/nginx/sites
END
    docker build -t ${NGINX} "${TMP}"
    rm -rf "${TMP}"
}

exit 0
