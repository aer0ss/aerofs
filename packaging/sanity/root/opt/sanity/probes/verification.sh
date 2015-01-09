#!/bin/bash
set -e
/opt/sanity/probes/tools/url.sh verification.service:8080/email
/opt/sanity/probes/tools/url.sh verification.service:8080/ldap
