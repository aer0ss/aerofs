#!/bin/bash
set -e -u

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BASEDIR="$(cd "$SCRIPT_DIR"; cd ../../; pwd)"

"$SCRIPT_DIR"/servlet sv "$BASEDIR" spsv

OUTPUT_DIR=build/sv

mkdir -p $OUTPUT_DIR/usr/bin/
cp "$BASEDIR"/src/spsv/resources/sv/clean_defects $OUTPUT_DIR/usr/bin/clean_defects
