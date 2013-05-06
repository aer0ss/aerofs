#!/bin/bash -e

SCHEMAS=../src/spsv/resources/schemas
OPT=spdb/opt/spdb

mkdir -p $OPT
cp $SCHEMAS/sp.sql $OPT/sp.sql
