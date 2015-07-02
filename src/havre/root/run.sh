#!/bin/bash
set -e

/container-scripts/copy-ca-cert /opt/havre
/container-scripts/import-ca-cert-to-java

# Generate certificates
#
# The CNAME is the output of the following Java code:
#     BaseSecUtil.getCertificateCName(UserID.DUMMY, new DID(UniqueID.ZERO))
# TODO (WW) dynamically generate the string?
/container-scripts/certify mfdokhaocdbgdfjdpdcifdckckebmlfmoakejeipndbedhfdpaofolfboadgdcnn /opt/havre/havre

echo Starting Havre...
cd /opt/havre
/container-scripts/restart-on-error java -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/var/log/havre -Xmx1536m -jar aerofs-havre.jar havre.properties
