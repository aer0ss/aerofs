#!/bin/bash -u -e

OPT=syncstat-tools/opt/syncstat-tools
mkdir -p $OPT
cp ../out.ant/artifacts/syncstat-tools/*.jar $OPT
