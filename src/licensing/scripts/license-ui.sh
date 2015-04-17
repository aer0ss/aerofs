#!/bin/bash
set -e -u

DATA_DIR=$HOME/license_data

REQUEST_FILE="$(mktemp)"
RESPONSE_FILE="$(mktemp)"

echo ">>> $REQUEST_FILE | $RESPONSE_FILE"

curl -o $REQUEST_FILE http://172.16.0.10:8000/download_csv

# Wait for flash drive to appear


if [[ -r $REQUEST_FILE ]] ; then
	# Run license tool
	$HOME/env/bin/license-tool --gpg-dir $HOME/license_root_key --data-dir "$DATA_DIR" --outfile=$RESPONSE_FILE signbundle "$REQUEST_FILE"
        curl -v -include --form license_bundle=@$RESPONSE_FILE http://172.16.0.10:8000/upload_bundle
else
	echo "Sorry, I don't see a CSV named \"$(basename $REQUEST_FILE)\"."
fi


rm $REQUEST_FILE
rm $RESPONSE_FILE
