#!/bin/bash

DEV_DIR="$(dirname "${BASH_SOURCE[0]:-$0}")/../../docker/dev"

# Type dk-help to see information about these commands

VM=docker-dev

alias dk-env='eval "$(docker-machine env ${VM})"'
function dk-create-vm()
{
    if [[ $# -eq 0 ]]
    then
        ${DEV_DIR}/dk-create-vm.sh ${VM} && dk-env
    else
        echo "dk-create-vm takes no arguments"
        return 1
    fi
}
function dk-create()
{
    if [[ $# -eq 0 ]]
    then
        dk-start-vm && ${DEV_DIR}/../../invoke --unsigned proto build_client package_clients build_docker_images && dk-reconfig
    else
        echo "dk-create takes no arguments"
        return 1
    fi
}
function dk-start-vm()
{
    if [[ $# -eq 0 ]]
    then
        ${DEV_DIR}/dk-start-vm.sh ${VM} && dk-env
    else
        echo "dk-start-vm takes no arguments"
        return 1
    fi
}
function dk-start()
{
    if [[ $# -eq 0 ]]
    then
        dk-start-vm && ${DEV_DIR}/emulate-ship.sh default
    else
        echo "dk-start takes no arguments"
        return 1
    fi
}
function dk-crane()
{
    ${DEV_DIR}/dk-crane.sh $@
}
function dk-destroy()
{
    if [[ $# -eq 0 ]]
    then
        ${DEV_DIR}/dk-destroy.sh
    else
        echo "dk-destroy takes no arguments"
        return 1
    fi
}
function dk-destroy-vm()
{
    if [[ $# -eq 0 ]]
    then
        docker-machine rm -f ${VM}
    else
        echo "dk-destroy-vm takes no arguments"
        return 1
    fi
}
function dk-exec()
{
    docker exec -it $@
}
function dk-halt()
{
    if [[ $# -eq 0 ]]
    then
        ${DEV_DIR}/dk-crane.sh kill -dall && ${DEV_DIR}/dk-crane.sh kill -dall maintenance
    else
        echo "dk-halt takes no arguments"
        return 1
    fi
}
function dk-help()
{
    ${DEV_DIR}/dk-help.sh $@
}
function dk-ip()
{
    if [[ $# -eq 0  ]]
    then
        docker-machine ip ${VM}
    else
        echo "dk-ip takes no arguments"
        return 1
    fi
}
function dk-reconfig()
{
    if [[ $# -eq 0 ]]
    then
        dk-start-vm && ${DEV_DIR}/dk-reconfig.sh no-create-first-user
    else
        echo "dk-reconfig takes no arguments"
        return 1
    fi
}
function dk-reload()
{
    ${DEV_DIR}/dk-reload.sh $@
}
function dk-restart()
{
    if [[ $# -eq 0 ]]
    then
        dk-halt && dk-start
    else
        echo "dk-restart takes no arguments"
        return 1
    fi
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
