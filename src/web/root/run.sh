#!/bin/bash
set -e

DEPLOYMENT_SECRET=$(cat /data/deployment_secret)
# Authorize a given service to bifrost, and print out client secret.
#
function authorize() {
    ID=$1
    NAME=$2
    EXPIRES=$3
    URL='bifrost.service:8700'
    AUTH_HEADER="Authorization: Aero-Service-Shared-Secret bootstrap $DEPLOYMENT_SECRET"

    RET=$(curl -S -s -H "$AUTH_HEADER" -o /dev/null -w "%{http_code}" $URL/clients/$ID)
    if [ $RET == 404 ]; then
        # The client hasn't registered. Register now.
        RET=$(curl -S -s -o /dev/null -w "%{http_code}" $URL/clients \
            -H "$AUTH_HEADER"
            --data-urlencode "client_id=$ID" \
            --data-urlencode "client_name=$NAME" \
            --data-urlencode "redirect_uri=aerofs://redirect" \
            --data-urlencode "resource_server_key=oauth-havre" \
            --data-urlencode "expires=$EXPIRES")
        if [ $RET != 200 ]; then
            echo "POST $URL/clients: status $RET"
            exit 66
        fi
    elif [ $RET != 200 ]; then
        echo "GET $URL/clients/$ID: status $RET"
        exit 99
    fi

    # Retrieve client secret
    RESP=$(curl -S -s -H "$AUTH_HEADER" $URL/clients/$ID)
    if [ x"$RESP" == x ]; then
        echo "GET $URL/clients/$ID returns error"
        exit 77
    fi

    # Print client secret
    SECRET=$(python -c "import json; print json.loads('$RESP')['secret']")
    if [ x"$SECRET" == x ]; then
        echo "No secret is found in response: $RESP"
        exit 88
    fi

    echo "$SECRET"
}

function render_production_ini() {
    SHELOB_SECRET="$1"
    ZELDA_SECRET="$2"
    SETTINGS_SECRET="$3"

    ENC_KEY_FILE=/data/web/session_encrypt_key
    VLD_KEY_FILE=/data/web/session_validate_key

    # Generate session encrypt and validate keys if not exist.
    if [ ! -f $ENC_KEY_FILE ]; then
        echo Creating session encryption key...
        openssl rand -hex 36 > $ENC_KEY_FILE
    fi
    if [ ! -f $VLD_KEY_FILE ]; then
        echo Creating session validation key...
        openssl rand -hex 36 > $VLD_KEY_FILE
    fi

    ENC_KEY=$(cat $ENC_KEY_FILE)
    VLD_KEY=$(cat $VLD_KEY_FILE)

    sed -e "s/{{ validate_key }}/$VLD_KEY/" \
        -e "s/{{ encrypt_key }}/$ENC_KEY/" \
        -e "s/{{ shelob_client_secret }}/$SHELOB_SECRET/" \
        -e "s/{{ zelda_client_secret }}/$ZELDA_SECRET/" \
        -e "s/{{ settings_client_secret }}/$SETTINGS_SECRET/" \
        /opt/web/production.ini.template > /opt/web/production.ini
}

echo Authorizing shelob to bifrost...
SHELOB_SECRET=$(authorize aerofs-shelob 'AeroFS Web Access' 900)

echo Authorizing zelda to bifrost...
ZELDA_SECRET=$(authorize aerofs-zelda 'AeroFS Link Sharing' 0)

echo Authorizing settings to bifrost...
SETTINGS_SECRET=$(authorize aerofs-settings 'AeroFS Settings' 0)

echo Write to production.ini...
render_production_ini "$SHELOB_SECRET" "$ZELDA_SECRET" "$SETTINGS_SECRET"

echo Starting pserve...
export PYTHONPATH=/opt/web
export STRIPE_PUBLISHABLE_KEY=dummy.stripe.key
export STRIPE_SECRET_KEY=dummy.stripe.secret

pserve /opt/web/production.ini
