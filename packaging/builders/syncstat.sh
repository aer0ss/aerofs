#!/bin/bash
set -e -u

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BASEDIR="$(cd "$SCRIPT_DIR"; cd ../../; pwd)"

"$SCRIPT_DIR"/servlet syncstat "$BASEDIR" syncstat
