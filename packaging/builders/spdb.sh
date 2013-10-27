#!/bin/bash
set -e -u

# We also want to add the schema to the package.
OUTPUT_DIR=build/spdb
OPT=$OUTPUT_DIR/opt/spdb
SCHEMAS=../src/spsv/resources/schemas

mkdir -p $OPT
cp $SCHEMAS/sp.sql $OPT/spdb.sql
