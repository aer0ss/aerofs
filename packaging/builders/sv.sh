#!/bin/bash -ue

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BASEDIR="$(cd "$SCRIPT_DIR"; cd ../../; pwd)"

"$SCRIPT_DIR"/servlet sv "$BASEDIR" spsv "${MODE:-STAGING}"
mkdir -p sv/usr/bin/
cp "$BASEDIR"/src/spsv/resources/sv/clean_defects sv/usr/bin/clean_defects
