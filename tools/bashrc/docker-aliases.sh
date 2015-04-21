#!/bin/bash

DEV_DIR="$(dirname "${BASH_SOURCE[0]:-$0}")/../../docker/dev"

# Type dk-help to see information about these commands

VM=docker-dev

alias dk-crane="${DEV_DIR}/dk-crane.sh"
alias dk-create="${DEV_DIR}/../build-images.sh --unsigned && ${DEV_DIR}/dk-reconfig.sh"
alias dk-createvm="docker-machine create -d virtualbox --virtualbox-disk-size 50000 --virtualbox-memory 3072 ${VM} && dk-env"
alias dk-destroy="${DEV_DIR}/dk-destroy.sh"
alias dk-destroyvm="docker-machine rm ${VM}"
alias dk-env='$(docker-machine env ${VM})'
alias dk-exec="docker exec -it"
alias dk-halt="${DEV_DIR}/dk-crane.sh kill -dall && ${DEV_DIR}/dk-crane.sh kill -dall maintenance"
alias dk-help="${DEV_DIR}/dk-help.sh"
alias dk-ip="docker-machine ip ${VM}"
alias dk-reconfig="${DEV_DIR}/dk-reconfig.sh"
alias dk-reload="${DEV_DIR}/dk-reload.sh"
alias dk-restart="${DEV_DIR}/dk-crane.sh kill -dall && ${DEV_DIR}/dk-crane.sh kill -dall maintenance && ${DEV_DIR}/dk-crane.sh run -dall"
alias dk-start="${DEV_DIR}/dk-crane.sh run -dall"

# Autocomplete
if [ -n "$(which brew)" ] && [ -f $(brew --prefix)/etc/bash_completion ]; then
 . $(brew --prefix)/etc/bash_completion
fi

bind "set completion-ignore-case on"
bind "set show-all-if-ambiguous on"

# Now, attempt to set up DOCKER_HOST and friends if docker-dev is running
[[ "$(docker-machine ls | grep ${VM} | grep Running)" ]] && dk-env
