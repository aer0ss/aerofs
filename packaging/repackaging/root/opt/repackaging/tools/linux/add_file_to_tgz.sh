#!/bin/bash
set -eu
# Produces tarball $2 by adding file $3 to source tarball $1 at path $4
TGZ_INPUT_FILENAME="$1"
TGZ_OUTPUT_FILENAME="$2"
FILE_TO_ADD="$3"
PATH_TO_ADD_FILE="$4"

NEW_FILE_BASENAME=$(basename "$FILE_TO_ADD")

echo "Inserting $FILE_TO_ADD in $TGZ_OUTPUT_FILENAME at $PATH_TO_ADD_FILE/$NEW_FILE_BASENAME"
WORK_FOLDER=$(mktemp -d)
echo "[+] Extracting source tarball $TGZ_INPUT_FILENAME to $WORK_FOLDER"
# extracting tars sometimes fail on CI for reasons unknown.
# Internet suggests that this is caused by a race and the observations are
# consistent with the explanation. So we add -P flag to see if this works.
# see http://lists.gnu.org/archive/html/bug-tar/2004-04/msg00021.html for
# detail.
tar xPf "$TGZ_INPUT_FILENAME" -C "$WORK_FOLDER"
echo "[+] Adding $FILE_TO_ADD to ${WORK_FOLDER}/${PATH_TO_ADD_FILE}"
mkdir -p "${WORK_FOLDER}/${PATH_TO_ADD_FILE}"
cp "$FILE_TO_ADD" "${WORK_FOLDER}/${PATH_TO_ADD_FILE}/${NEW_FILE_BASENAME}"
echo "[+] Creating new tarball $TGZ_OUTPUT_FILENAME"
tar czf "${TGZ_OUTPUT_FILENAME}" -C "${WORK_FOLDER}" $(ls "$WORK_FOLDER")
rm -rf "$WORK_FOLDER"
