#!/bin/bash
set -ex

if [ $# != 1 ]; then
    echo "This script compress the logs from all the appliance containers to a single file."
    echo "Usage: $0 <log-archive-tgz-path>"
    exit 11
fi

OUTPUT="$1"

# List all the appliance containers. The loader container name must be consistent with the name defined in emulate.sh
CONTAINERS="$(docker exec loader curl -sS localhost/v1/containers | jq -r 'keys[]')"
[[ -n "${CONTAINERS}" ]] || {
    echo "ERROR: empty contianer list"
    exit 22
}

TMP_DIR="$(mktemp -t -d XXXXXX)"
mkdir -p "${TMP_DIR}"/logs

for i in ${CONTAINERS}; do
    set +e
    docker logs --timestamps ${i} >"${TMP_DIR}"/logs/${i}.log 2>&1
    set -e
done

cd "${TMP_DIR}"
tar zcf logs.tgz logs
cd -

rm -rf "${OUTPUT}"
mv "${TMP_DIR}"/logs.tgz "${OUTPUT}"
rm -rf "${TMP_DIR}"