#!/bin/bash
set -e

THIS_DIR="$(dirname "$0")"

docker build -t signup-decoder "${THIS_DIR}"
docker run -d --name signup-decoder -p 21337:21337 -v /var/run/docker.sock:/var/run/docker.sock signup-decoder
