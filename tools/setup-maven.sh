#!/bin/bash
PROJ_DIR="$(dirname $0)/setup-maven"

pushd "$PROJ_DIR"
mvn compile
popd
