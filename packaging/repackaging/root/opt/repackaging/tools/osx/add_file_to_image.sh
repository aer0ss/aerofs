#!/bin/bash
set -eu

# Produces dmg $2 by adding file $3 to source dmg $1 at path $4
# Example:
#   ./add_file_to_image.sh \
# 		AeroFSInstall.dmg \
# 		AeroFSInstallCustom.dmg \
# 		site-config.properties \
# 		/AeroFS.app/Contents/Resources/site-config.lproj/locversion.plist

IMAGE_INPUT_FILENAME="$1"
IMAGE_OUTPUT_FILENAME="$2"
FILE_TO_ADD="$3"
DEST_PATH="$4"

HFS_INTERMEDIATE="${IMAGE_INPUT_FILENAME}.hfs"

absent() {
	# the hfsplus tools doesn't report failures via exit codes, so we
	# screenscrape the output instead.
	set +e
	hfsplus "$1" ls "$2" | grep "No such file or directory" >/dev/null
	RESULT=$?
	set -e
	return $RESULT
}

# Recursive mkdir -p
ensure_exists() {
	local HFS_FILE="$1"
	local TARGET="$2"
	# Base case: / and . are guaranteed to exist
	if [[ "${TARGET}" = "/" ]] || [[ "${TARGET}" = "." ]] ; then
		return
	fi

	local PARENT=$(dirname "${TARGET}")
	if absent "${HFS_FILE}" "${TARGET}" ; then
		ensure_exists "${HFS_FILE}" "${PARENT}"
		hfsplus "${HFS_FILE}" mkdir "${TARGET}"
	fi
}

echo "[+] Inserting ${FILE_TO_ADD} in ${IMAGE_OUTPUT_FILENAME} at ${DEST_PATH}/${FILE_TO_ADD}"

echo "[++] Extracting HFS+ image..."
dmg extract "${IMAGE_INPUT_FILENAME}" "${HFS_INTERMEDIATE}" >/dev/null

DEST_PARENT=$(dirname "${DEST_PATH}")
ensure_exists "${HFS_INTERMEDIATE}" "${DEST_PARENT}"

echo "[++] Adding ${FILE_TO_ADD} as ${DEST_PATH}..."
hfsplus "${HFS_INTERMEDIATE}" add "${FILE_TO_ADD}" "${DEST_PATH}"

echo "[++] Recompressing HFS+ image..."
dmg build "${HFS_INTERMEDIATE}" "$IMAGE_OUTPUT_FILENAME" >/dev/null

rm "${HFS_INTERMEDIATE}"
