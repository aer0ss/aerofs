#!/bin/bash

DEV_DIR="$(dirname "${BASH_SOURCE[0]:-$0}")/../../docker/dev"

# Type dk-help to see information about these commands

VM=docker-dev

function dk-create-vm()
{
    if [[ $# -eq 0 ]]
    then
        ${DEV_DIR}/dk-create-vm.sh ${VM}
    else
        echo "dk-create-vm takes no arguments"
        return 1
    fi
}
function dk-create()
{
    if [[ $# -eq 0 ]]
    then
        dk-start-vm && ${DEV_DIR}/../../invoke --unsigned proto build_client package_clients package_updates build_images && dk-reconfig
    else
        echo "dk-create takes no arguments"
        return 1
    fi
}
function dk-start-vm()
{
    if [[ $# -eq 0 ]]
    then
        # Filter out docker-machine env hint, as it's not relevant in this context, since we're
        # doing a dk-env immediately after.
        ${DEV_DIR}/dk-start-vm.sh ${VM} | grep -v "You may need to re-run the \`docker-machine env\` command."
    else
        echo "dk-start-vm takes no arguments"
        return 1
    fi
}
function dk-start()
{
    if [[ $# -eq 0 ]]
    then
        dk-start-vm && ${DEV_DIR}/emulate-ship.sh aerofs/loader default
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
        colima delete -f -p ${VM}
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
        ${DEV_DIR}/dk-crane.sh kill -dall && ${DEV_DIR}/dk-crane.sh kill -dall maintenance && ${DEV_DIR}/dk-crane.sh kill -dall default
    else
        echo "dk-halt takes no arguments"
        return 1
    fi
}
function dk-halt-vm()
{
    if [[ $# -eq 0 ]]
    then
        colima stop ${VM}
    else
        echo "dk-halt-vm takes no arguments"
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
        colima status docker-dev --json | jq -r .ip_address
    else
        echo "dk-ip takes no arguments"
        return 1
    fi
}
function dk-ps()
{
    if [[ $# -eq 0 ]]
    then
        if [[ "$(docker ps | wc -l)" -eq "1" ]]
        then
            echo "No running container."
        else
            docker stats $(docker ps | tail -n+2 | awk -F' ' '{print $NF}')
        fi
    else
        echo "dk-ps takes no arguments"
        return 1
    fi
}
function dk-reconfig()
{
    if [[ $# -eq 0 || $# -eq 1 ]]
    then
        dk-start-vm && ${DEV_DIR}/dk-reconfig.sh no-create-first-user $@
    else
        echo "dk-reconfig takes at most one argument"
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

