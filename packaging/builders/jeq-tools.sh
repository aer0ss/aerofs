#!/bin/bash -u -e

OPT=jeq-tools/opt/jeq-tools
mkdir -p $OPT
cp ../out.ant/artifacts/jeq-enqueue-unlink/*.jar $OPT
cp ../out.ant/artifacts/jeq-enqueue-erase/*.jar $OPT
