#!/bin/bash
set -eu

docker build --network aero-bridge -t shipenterprise/vm-loader "$(dirname "$0")"
echo >&2 -e "\033[32mok: \033[0m- build shipenterprise/vm-loader"
