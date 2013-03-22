#!/bin/bash -u -e

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BASEDIR="$(cd "$SCRIPT_DIR"; cd ../../; pwd)"

"$SCRIPT_DIR"/servlet sv "$BASEDIR" spsv "${MODE:-STAGING}"
mkdir -p sv/usr/local/bin/
cp "$BASEDIR"/src/spsv/resources/sv/clean_defects sv/usr/local/bin/clean_defects
