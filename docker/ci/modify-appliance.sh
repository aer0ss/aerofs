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

# Set log level to DEBUG for verbosity
CONFIG="${REGISTRY}aerofs/config:${TAG}"
echo "Modifying ${CONFIG} ..."
true && {
    TMP="$(mktemp -d -t XXXXXX)"
    cat > "${TMP}/Dockerfile" <<END
FROM ${CONFIG}
RUN  sed -i \
        -e s/log_level=INFO/log_level=DEBUG/ \
        -e s/analytics_endpoint=.*/analytics_endpoint=/ \
        /external.properties.docker.default
RUN  echo -e "syncstatus_enabled=true\n" >> /external.properties.docker.default
END
    docker build -t ${CONFIG} "${TMP}"
    rm -rf "${TMP}"
}

# Enable disabled services in Nginx
NGINX="${REGISTRY}aerofs/nginx:${TAG}"
echo "Modifying ${NGINX} ..."
[[ "$(docker run --rm "${NGINX}" ls '/etc/nginx/sites-disabled')" ]] && {
    TMP="$(mktemp -d -t XXXXXX)"
    cat > "${TMP}/Dockerfile" <<END
FROM ${NGINX}
RUN mv /etc/nginx/sites-disabled/* /etc/nginx/sites
END
    docker build -t ${NGINX} "${TMP}"
    rm -rf "${TMP}"
}

exit 0
