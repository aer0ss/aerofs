#!/bin/bash

DEV_DIR="$(dirname "${BASH_SOURCE[0]:-$0}")/../../docker/dev"

# Type dk-help to see information about these commands

VM=docker-dev

alias dk-env='eval "$(docker-machine env ${VM})"'
function dk-create-vm()
{
    ${DEV_DIR}/dk-create-vm.sh ${VM} && dk-env
}
function dk-create()
{
    dk-start-vm && ${DEV_DIR}/../../invoke --unsigned proto build_client package_clients build_docker_images && dk-reconfig
}
function dk-start-vm()
{
    ${DEV_DIR}/dk-start-vm.sh ${VM} && dk-env
}
function dk-start()
{
    dk-start-vm && ${DEV_DIR}/emulate-ship.sh default
}
function dk-crane()
{
    ${DEV_DIR}/dk-crane.sh
}
function dk-destroy()
{
    ${DEV_DIR}/dk-destroy.sh
}
function dk-destroy-vm()
{
    docker-machine rm -f ${VM}
}
function dk-exec()
{
    docker exec -it
}
function dk-halt()
{
    ${DEV_DIR}/dk-crane.sh kill -dall && ${DEV_DIR}/dk-crane.sh kill -dall maintenance
}
function dk-help()
{
    ${DEV_DIR}/dk-help.sh
}
function dk-ip()
{
    docker-machine ip ${VM}
}
function dk-reconfig()
{
    dk-start-vm && ${DEV_DIR}/dk-reconfig.sh no-create-first-user
}
function dk-reload()
{
    ${DEV_DIR}/dk-reload.sh
}
function dk-restart()
{
    dk-halt && dk-start
}

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
