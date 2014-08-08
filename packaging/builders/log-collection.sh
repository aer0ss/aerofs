#!/bin/bash
set -e -u

SCRIPT_DIR="$( cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BASEDIR="$(cd "$SCRIPT_DIR"; cd ../../; pwd)"

# The log collection servlet Java code is currently part of the spsv Java package.
# Once we split the Java code this param can be updated.
"$SCRIPT_DIR"/generators/generate_servlet_template.sh log-collection "$BASEDIR" spsv
