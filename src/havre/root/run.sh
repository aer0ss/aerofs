#!/bin/bash
set -e

/container-scripts/copy-ca-cert /opt/havre

LOG_LEVEL="$(/container-scripts/get-config-property base.log.level)"

# Generate certificates
#
# The CNAME is the output of the following Java code:
#     BaseSecUtil.getCertificateCName(UserID.DUMMY, new DID(UniqueID.ZERO))
# TODO (WW) dynamically generate the string?
/container-scripts/certify mfdokhaocdbgdfjdpdcifdckckebmlfmoakejeipndbedhfdpaofolfboadgdcnn /opt/havre/havre

echo Starting Havre...
cd /opt/havre

sed -e "s/{{ log_level }}/$LOG_LEVEL/g" \
    havre.properties.tmplt > havre.properties

/container-scripts/restart-on-error java -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/havre -Xmx1536m -jar aero-havre.jar havre.properties
