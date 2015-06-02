#!/bin/bash

DEV_DIR="$(dirname "${BASH_SOURCE[0]:-$0}")/../../docker/dev"

# Type dk-help to see information about these commands

VM=docker-dev

alias dk-create-vm="${DEV_DIR}/dk-create-vm.sh ${VM} && dk-env"
alias dk-create="dk-start-vm && ${DEV_DIR}/../build-images.sh --unsigned && dk-reconfig"
alias dk-start-vm="${DEV_DIR}/dk-start-vm.sh ${VM} && dk-env"
alias dk-start="dk-start-vm && ${DEV_DIR}/emulate-ship.sh default"
alias dk-crane="${DEV_DIR}/dk-crane.sh"
alias dk-destroy="${DEV_DIR}/dk-destroy.sh"
alias dk-destroy-vm="docker-machine rm -f ${VM}"
alias dk-env='eval "$(docker-machine env ${VM})"'
alias dk-exec="docker exec -it"
alias dk-halt="${DEV_DIR}/dk-crane.sh kill -dall && ${DEV_DIR}/dk-crane.sh kill -dall maintenance"
alias dk-help="${DEV_DIR}/dk-help.sh"
alias dk-ip="docker-machine ip ${VM}"
alias dk-reconfig="dk-start-vm && ${DEV_DIR}/dk-reconfig.sh no-create-first-user"
alias dk-reload="${DEV_DIR}/dk-reload.sh"
alias dk-restart="dk-halt && dk-start"

# Autocomplete
command -v autoload &>/dev/null
if [ $? -eq 0 ] ; then
    autoload bashcompinit
    bashcompinit
    alias shopt=':'
fi

if [ $(uname -s) = "Darwin" ] && [ $SHELL != "/bin/zsh" ] && [ -n "$(which brew)" ] && [ -f $(brew --prefix)/etc/bash_completion ]; then
 . $(brew --prefix)/etc/bash_completion
fi

command -v bind &>/dev/null
if [ $? -eq 0 ] ; then
 # The bind command is supported on bash only
 bind "set completion-ignore-case on"
 bind "set show-all-if-ambiguous on"
fi

# Now, attempt to set up DOCKER_HOST and friends if docker-dev is running
[[ -n "$(docker-machine ls | grep ${VM} | grep Running)" ]] && dk-env
