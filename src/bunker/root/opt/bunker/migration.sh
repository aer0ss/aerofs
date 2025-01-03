#!/bin/bash
set -eu

# requires root

PASS=$(< aerofs-db-backup/ca-files/passwd)
export PASS

# mysql on ubuntu has a bug where load_file only works within mysql's home directory (/var/lib/mysql)
# on files owned by the mysql user
CA_DATA=/var/lib/mysql/ca_data
rm -rf "$CA_DATA"
mkdir -p "$CA_DATA"
KEY_LOCATION="$CA_DATA"/key
CERT_LOCATION="$CA_DATA"/cert

openssl rsa -in aerofs-db-backup/ca-files/private/ca-key.pem -out "$KEY_LOCATION" -passin env:PASS -outform der
openssl x509 -in aerofs-db-backup/ca-files/cacert.pem -out "$CERT_LOCATION" -outform der

#requires mysql schema to be set up
mysql -h mysql.service aerofs_ca -se "DELETE FROM server_configuration;" -se "DELETE FROM signed_certificates;" \
    -se "INSERT INTO server_configuration(ca_key, ca_cert) VALUES(LOAD_FILE('$KEY_LOCATION'), LOAD_FILE('$CERT_LOCATION'));"

SERIAL=$(openssl x509 -in aerofs-db-backup/ca-files/cacert.pem -serial -noout | cut -d "=" -f 2)
# cast the cacert's serial as signed because it's the only serial number randomly generated by the old ca-server, and might be higher than 2^63 (mysql defaults to interpreting as unsigned)
# NOTE: BB applied this script before this fix, so they most likely have an incorrect row in signed_certificates for their cacert
mysql -h mysql.service aerofs_ca -se "INSERT INTO signed_certificates(serial_number, certificate) VALUES(CAST(X'$SERIAL' as SIGNED), LOAD_FILE('$CERT_LOCATION'));"

for cert in $( ls aerofs-db-backup/ca-files/certs/*.cert ); do
    SERIAL=$(openssl x509 -in $cert -serial -noout | cut -d "=" -f 2)
    CERT=$(openssl x509 -in $cert -out $CERT_LOCATION -outform der)
    mysql -h mysql.service aerofs_ca -se "INSERT INTO signed_certificates(serial_number, certificate) VALUES(X'$SERIAL', LOAD_FILE('$CERT_LOCATION'));"
done

rm -rf $CA_DATA
