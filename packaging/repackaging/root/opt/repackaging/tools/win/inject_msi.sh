#!/bin/bash
set -eu

if [ $# -ne 4 ] ; then
    echo "Usage: $(basename $0) SITE_CONFIG BASE_MSI OUTPUT_MSI WORKSPACE"
    exit 1
fi

SITE_CONFIG=$1
BASE_MSI=$2
OUTPUT_MSI=$3
WORKSPACE=$4

TEMP_MSI_NAME="temp.msi"
TEMP_MSI="${WORKSPACE}/${TEMP_MSI_NAME}"
CAB_FILE="cab1.cab"
SITE_CONFIG_FILE_NAME="site-config.properties"
SITE_CONFIG_FILE_SIZE=$(wc -c "${SITE_CONFIG}" | awk '{print $1}')

FILE_ID="filSiteConfig"
COMP_ID="cmpSiteConfig"
GUID="{$(uuidgen)}"

mkdir -p "${WORKSPACE}"

cp "${BASE_MSI}" "${TEMP_MSI}"

# doctor enterprise site config.
SITE_CONFIG_TARGET="${WORKSPACE}/${FILE_ID}"

cp "${SITE_CONFIG}" "${SITE_CONFIG_TARGET}"

# inject the enterprise site config into the archive
pushd "${WORKSPACE}" >>/dev/null
msiinfo extract "${TEMP_MSI_NAME}" "${CAB_FILE}" > "${CAB_FILE}"

FILE_LIST=$(gcab -t "${CAB_FILE}")
FILE_LIST_SIZE=$(gcab -t "${CAB_FILE}" | wc -l | awk '{print $1}')

gcab -x "${CAB_FILE}"
# Note that file indicies are referenced in the MSI database table so the order
# _is_ important here!
gcab -zc "${CAB_FILE}" ${FILE_LIST} "${FILE_ID}"

msibuild "${TEMP_MSI_NAME}" -a "${CAB_FILE}" "${CAB_FILE}"
popd >>/dev/null

# update the databases
FILE_LIST_SIZE=$((${FILE_LIST_SIZE} + 1))

msibuild "${TEMP_MSI}" -q "insert into Component(Component, ComponentId, Directory_, Attributes, KeyPath) values('${COMP_ID}', '${GUID}', 'INSTALLLOCATION', 0, '${FILE_ID}');"
msibuild "${TEMP_MSI}" -q "insert into FeatureComponents(Feature_, Component_) values('AeroFSFeatures', '${COMP_ID}');"
msibuild "${TEMP_MSI}" -q "insert into File(File, Component_, FileName, FileSize, Attributes, Sequence) values('${FILE_ID}', '${COMP_ID}', '${SITE_CONFIG_FILE_NAME}', '${SITE_CONFIG_FILE_SIZE}', 512, ${FILE_LIST_SIZE});"
msibuild "${TEMP_MSI}" -q "update Media set LastSequence=${FILE_LIST_SIZE} where DiskId=1;"

# done
cp "${TEMP_MSI}" "${OUTPUT_MSI}"
rm -rf "${WORKSPACE}"
