#!/bin/bash
set -e

/container-scripts/create-database aerofs_sp
/run-tomcat.sh
