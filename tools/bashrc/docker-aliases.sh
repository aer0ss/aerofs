#!/bin/bash

DEV_DIR="$(dirname "${BASH_SOURCE[0]:-$0}")/../../docker/dev"

# Type dk-help to see information about these commands

alias dk-help="${DEV_DIR}/dk-help.sh"
alias dk-crane="${DEV_DIR}/dk-crane.sh"
alias dk-create="${DEV_DIR}/dk-create.sh"
alias dk-reconfig="${DEV_DIR}/dk-create.sh nobuild"
alias dk-reload="${DEV_DIR}/dk-reload.sh"
alias dk-exec="docker exec -it"
alias dk-destroy="${DEV_DIR}/dk-destroy.sh"
alias dk-start="${DEV_DIR}/dk-crane.sh run -dall"
alias dk-halt="${DEV_DIR}/dk-crane.sh kill -dall"
alias dk-restart="${DEV_DIR}/dk-crane.sh kill -dall && ${DEV_DIR}/dk-crane.sh run -dall"
