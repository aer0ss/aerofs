#!/bin/bash
set -e -u -x
# To reduce the number of scripts we put in heredocs, I've made this a separate
# script.  It expects to be passed the path to an .exe file that it should
# extract and upload to kaspersky's whitelist ftp server.
EXE_FILE="$1"
WORK_DIR=$(mktemp -d kaspersky_upload.XXXXXX)
pushd $WORK_DIR
    # Extract the exe into WORK_DIR
    7z x "$EXE_FILE" '-i!$_OUTDIR'
    mv '$_OUTDIR'/* ./
    rmdir '$_OUTDIR'
    7z x "$EXE_FILE" '-x!$PLUGINSDIR' '-x!$COMMONFILES' '-x!$_OUTDIR'
    # Upload extracted files (and installer exe!) to kaspsersky
    lftp -e "mirror -R ./ /aerofs/; bye" -u $(cat ~/.lftp/login.cfg) whitelist1.kaspersky-labs.com
    lftp -e "put $EXE_FILE; bye" -u $(cat ~/.lftp/login.cfg) whitelist1.kaspersky-labs.com
popd
rm -rf $WORK_DIR
# Delete self upon completion.
rm "${BASH_SOURCE[0]}"
