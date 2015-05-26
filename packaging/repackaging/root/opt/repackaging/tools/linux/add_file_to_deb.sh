#!/bin/bash
set -eu
# Produces deb $2 by adding file $3 to source deb $1 at path $4

DEB_INPUT_FILENAME="$1"
DEB_OUTPUT_FILENAME="$2"
FILE_TO_ADD="$3"
PATH_TO_ADD_FILE="$4"

NEW_FILE_BASENAME=$(basename "$FILE_TO_ADD")

echo "Inserting $FILE_TO_ADD in $DEB_OUTPUT_FILENAME at $PATH_TO_ADD_FILE/$NEW_FILE_BASENAME"
WORK_FOLDER=$(mktemp -d)
echo "[+] Extracting source deb $DEB_INPUT_FILENAME to $WORK_FOLDER"
dpkg-deb --raw-extract "$DEB_INPUT_FILENAME" "$WORK_FOLDER"
echo "[+] Adding $FILE_TO_ADD to ${WORK_FOLDER}/${PATH_TO_ADD_FILE}"
mkdir -p "${WORK_FOLDER}/${PATH_TO_ADD_FILE}"
cp -a "$FILE_TO_ADD" "${WORK_FOLDER}/${PATH_TO_ADD_FILE}/${NEW_FILE_BASENAME}"
echo "[+] Creating new deb $DEB_OUTPUT_FILENAME"
fakeroot dpkg-deb --build "${WORK_FOLDER}" "${DEB_OUTPUT_FILENAME}"
rm -rf "$WORK_FOLDER"
