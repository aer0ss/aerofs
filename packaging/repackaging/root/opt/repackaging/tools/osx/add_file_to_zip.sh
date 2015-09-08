#!/bin/bash
set -eu

# Produces zip $2 by adding file $3 to source zip $1 at path $4 with name $5

ZIP_INPUT_FILENAME="$1"
ZIP_OUTPUT_FILENAME="$2"
FILE_TO_ADD="$3"
PATH_TO_ADD_FILE="$4"
DEST_FILENAME="$5"

OUTPUT_PREFIX=""
# If ZIP_OUTPUT_FILENAME is an absolute path, we don't need to prefix it when
# referring to it below after we've changed PWD.
# If it's relative, then we should prefix it with the current PWD.
[ "${ZIP_OUTPUT_FILENAME#/}" == "${ZIP_OUTPUT_FILENAME}" ] && OUTPUT_PREFIX="$PWD/"

echo "[+] Inserting ${FILE_TO_ADD} in ${ZIP_OUTPUT_FILENAME} at ${PATH_TO_ADD_FILE}/${DEST_FILENAME}"
WORK_FOLDER=$(mktemp -d)

cp "${ZIP_INPUT_FILENAME}" "${ZIP_OUTPUT_FILENAME}"

echo "[++] Adding ${FILE_TO_ADD} to ${WORK_FOLDER}/${PATH_TO_ADD_FILE} as ${DEST_FILENAME}"
mkdir -p "${WORK_FOLDER}/${PATH_TO_ADD_FILE}"
cp "${FILE_TO_ADD}" "${WORK_FOLDER}/${PATH_TO_ADD_FILE}/${DEST_FILENAME}"

echo "[++] Creating new zip ${OUTPUT_PREFIX}${ZIP_OUTPUT_FILENAME}"
pushd "${WORK_FOLDER}" >>/dev/null
zip -r "${OUTPUT_PREFIX}${ZIP_OUTPUT_FILENAME}" "${PATH_TO_ADD_FILE}/${DEST_FILENAME}"
popd >>/dev/null

rm -rf "$WORK_FOLDER"
