#!/bin/bash -u -e

OPT=syncstat-tools/opt/syncstat-tools
mkdir -p $OPT
cp ../out.ant/artifacts/syncstat-generate-histograms/*.jar $OPT
cp ../out.ant/artifacts/syncstat-list-heavy-devices/*.jar $OPT
