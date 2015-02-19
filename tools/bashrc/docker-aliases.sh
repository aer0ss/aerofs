#!/bin/bash

DEV_DIR="$(dirname "${BASH_SOURCE[0]}")/../../docker/dev"

# Run crane from any folder
alias crane="${DEV_DIR}/crane.sh"

alias dk-create="${DEV_DIR}/dk-create.sh"
alias dk-reload="${DEV_DIR}/dk-reload.sh"
alias dk-destroy="${DEV_DIR}/dk-destroy.sh"
alias dk-start="${DEV_DIR}/crane.sh run -dall"
alias dk-halt="${DEV_DIR}/crane.sh kill -dall"
