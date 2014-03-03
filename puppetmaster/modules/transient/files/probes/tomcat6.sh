#!/bin/bash -e
/opt/sanity/probes/tools/port.sh localhost 8080
/opt/sanity/probes/tools/url.sh https://localhost:4433/sp/
/opt/sanity/probes/tools/url.sh http://localhost:8080/verification/ldap
/opt/sanity/probes/tools/url.sh http://localhost:8080/verification/email
# TODO (MP) add probe for identity servlet here.
