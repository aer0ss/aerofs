#!/bin/bash
set -e

/container-scripts/create-schema aerofs_sp
/run-tomcat.sh
