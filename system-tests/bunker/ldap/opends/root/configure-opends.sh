#!/bin/bash
set -ex

OpenDS-2.2.1/setup --cli --rootUserPassword aaaaaa --adminConnectorPort 4444 --ldapPort 389 --sampleData 100 --rootUserDN 'cn=root' --baseDN 'dc=example,dc=com' --doNotStart <<END
no
no
1
END
