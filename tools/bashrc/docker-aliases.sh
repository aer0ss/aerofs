#!/bin/bash

DEV_DIR="$(dirname "${BASH_SOURCE[0]:-$0}")/../../docker/dev"

# Run crane from any folder
alias dk-crane="${DEV_DIR}/dk-crane.sh"

# Rebuild, recreate, and configure containers
alias dk-create="${DEV_DIR}/dk-create.sh"

# Recreate and configure containers but do not rebuild them
alias dk-reconfig="${DEV_DIR}/dk-create.sh nobuild"

# Recreate specified container and all the containers that depend on it.
# Do not recreate Data container. Otherwise you need to run dk-reconfig to reconfig the system
alias dk-reload="${DEV_DIR}/dk-reload.sh"

alias dk-exec="docker exec -it"

# Remove, start, stop, and restart all containers
alias dk-destroy="${DEV_DIR}/dk-destroy.sh"
alias dk-start="${DEV_DIR}/dk-crane.sh run -dall"
alias dk-halt="${DEV_DIR}/dk-crane.sh kill -dall"
alias dk-restart="${DEV_DIR}/dk-crane.sh kill -dall && ${DEV_DIR}/dk-crane.sh run -dall"
