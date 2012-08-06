#!/bin/bash -u -e

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BASEDIR="$(cd "$SCRIPT_DIR"; cd ../../; pwd)"

"$SCRIPT_DIR"/servlet syncstat "$BASEDIR" syncstat "${MODE:-STAGING}"
