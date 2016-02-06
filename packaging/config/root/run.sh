#!/bin/bash
set -e

PROP_FILE=/opt/config/properties/external.properties
if [ ! -f $PROP_FILE ]; then
    echo Creating $PROP_FILE...
    cat /external.properties.default /external.properties.docker.default > $PROP_FILE
fi

# Add default properties specific to the dockerized appliance if they aren't present.
for i in $(cat /external.properties.docker.default); do
    KEY=$(echo "$i" | sed -e "s/=.*//")
    if [ -z "$(grep "^${KEY}=" ${PROP_FILE})" ]; then
        echo "${i}" >> ${PROP_FILE}
    fi
done

# The string replacement is to support restoring from legacy appliances.
sed -i "s/email_host=localhost/email_host=postfix.service/" ${PROP_FILE}

# Replace open_signup=true with corresponding signup_restriction value.
sed -i "s/open_signup=true/signup_restriction=UNRESTRICTED/" ${PROP_FILE}

# N.B. open_signup=false will only appear in external.properties if open_signup was
# made to be true at some point i.e. by default if the admin never changed the open_signup
# value open_signup woudn't appear in external.properties.
sed -i "s/open_signup=false/signup_restriction=USER_INVITED/" ${PROP_FILE}

# -u to disable console print caching
/container-scripts/restart-on-error python -u /opt/config/entry.py
