#!/bin/bash
set -e

/container-scripts/create-database aerofs_sp
/container-scripts/create-database bifrost
/run-tomcat.sh
