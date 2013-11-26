#!/bin/bash
set -e -u

OUTPUT_DIR=build/syncstat-tools
OPT=$OUTPUT_DIR/opt/syncstat-tools

mkdir -p $OPT
cp ../out.ant/artifacts/syncstat-generate-histograms/*.jar $OPT
cp ../out.ant/artifacts/syncstat-list-heavy-devices/*.jar $OPT
