#!/bin/bash
set -ex

# See http://docs.oracle.com/cd/E19450-01/820-6171/setup.html for OpenDS documentation
OpenDS-2.2.1/setup --cli --rootUserPassword aaaaaa --adminConnectorPort 4444 --ldapPort 389 --sampleData 100 --rootUserDN 'cn=root' --baseDN 'dc=example,dc=com' --generateSelfSignedCertificate --enableStartTLS --ldapsPort 636 --doNotStart <<END
no
no
1
END

# Export SelfSignedCertificate in PEM format
keytool -export -rfc -alias server-cert -keystore /OpenDS-2.2.1/config/keystore -storepass `cat /OpenDS-2.2.1/config/keystore.pin` -file server-cert.pem
