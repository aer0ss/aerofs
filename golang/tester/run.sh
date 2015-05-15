#!/bin/bash

THIS_DIR="$(dirname "$0")"

docker build -t aerofs-golang-tester -f "$THIS_DIR/Dockerfile" "$THIS_DIR/.."

docker run --rm aerofs-golang-tester "$@"

