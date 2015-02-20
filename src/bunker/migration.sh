#!/bin/bash
set -eu

# requires root

PASS=$(< /opt/ca/prod/passwd)
export PASS

# mysql on ubuntu has a bug where load_file only works within mysql's home directory (/var/lib/mysql)
# on files owned by the mysql user
CA_DATA=/var/lib/mysql/ca_data
rm -rf "$CA_DATA"
mkdir -p "$CA_DATA"
KEY_LOCATION="$CA_DATA"/key
CERT_LOCATION="$CA_DATA"/cert

openssl rsa -in /opt/ca/prod/private/ca-key.pem -out "$KEY_LOCATION" -passin env:PASS -outform der
openssl x509 -in /opt/ca/prod/cacert.pem -out "$CERT_LOCATION" -outform der

chown -R mysql:mysql "$CA_DATA"

#requires mysql schema to be set up

mysql aerofs_ca -se "DELETE FROM server_configuration;" -se "DELETE FROM signed_certificates;" \
    -se "INSERT INTO server_configuration(ca_key, ca_cert) VALUES(LOAD_FILE('$KEY_LOCATION'), LOAD_FILE('$CERT_LOCATION'));"

SERIAL=$(openssl x509 -in /opt/ca/prod/cacert.pem -serial -noout | cut -d "=" -f 2)
mysql aerofs_ca -se "INSERT INTO signed_certificates(serial_number, certificate) VALUES('$SERIAL', LOAD_FILE('$CERT_LOCATION'));"

for cert in $( ls /opt/ca/prod/certs/*.cert ); do
    SERIAL=$(openssl x509 -in $cert -serial -noout | cut -d "=" -f 2)
    CERT=$(openssl x509 -in $cert -out $CERT_LOCATION -outform der)
    mysql aerofs_ca -se "INSERT INTO signed_certificates(serial_number, certificate) VALUES(X'$SERIAL', LOAD_FILE('$CERT_LOCATION'));"
done

rm -rf $CA_DATA
