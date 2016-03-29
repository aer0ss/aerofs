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
        dk-start-vm && ${DEV_DIR}/../../invoke --unsigned proto build_client package_clients build_images && dk-reconfig
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
        dk-env
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
        # find name of hostonly adapter used by docker-machine
        adapter=$(VBoxManage showvminfo --machinereadable docker-dev | grep hostonlyadapter | cut -d '"' -f 2)

        docker-machine rm -f ${VM}

        # cleanup hostonlyif and attached DHCP server to ensure the next dk-create
        # gets the correct IP (192.168.99.100) instead of a successor
        VBoxManage dhcpserver remove --ifname $adapter
        VBoxManage hostonlyif remove $adapter
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
        ${DEV_DIR}/dk-crane.sh kill -dall && ${DEV_DIR}/dk-crane.sh kill -dall maintenance && ${DEV_DIR}/dk-crane.sh kill -dall onboard
    else
        echo "dk-halt takes no arguments"
        return 1
    fi
}
function dk-halt-vm()
{
    if [[ $# -eq 0 ]]
    then
        docker-machine stop ${VM}
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
        docker-machine ip ${VM}
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
function sa-crane()
{
    ${DEV_DIR}/gen-sa-crane-yml.sh
    crane $@ -c ${DEV_DIR}/../sa-crane.yml
}
function sa-start()
{
    if [[ $# -eq 0 ]]
    then
        dk-start-vm && ${DEV_DIR}/emulate-ship.sh aerofs/sa-loader default
    else
        echo "dk-start takes no arguments"
        return 1
    fi
}
function sa-halt()
{
    if [[ $# -eq 0 ]]
    then
        sa-crane kill -dall && sa-crane kill -dall maintenance
    else
        echo "sa-halt takes no arguments"
        return 1
    fi
}

function sa-reload()
{
    if [[ $# -eq 0 ]]
    then
        echo "sa-reload takes at least 1 container name"
        return 1
    else
        sa-crane run --recreate -aall $@
    fi
}
function sa-create()
{
    # order is important here, sa-destroy is also used to modify the sa-loader image to have a dev-compatibile crane file
    # thus, we have to build the sa-loader before we call sa-destroy, lest the modified image be rewritten
    if [[ $# -eq 0 ]]
    then
        dk-start-vm && ${DEV_DIR}/../../invoke --unsigned build_sa_images && sa-destroy && ${DEV_DIR}/emulate-ship.sh aerofs/sa-loader maintenance
    else
        echo "sa-create takes no arguments"
        return 1
    fi
}
function sa-destroy()
{
    if [[ $# -eq 0 ]]
    then
        ${DEV_DIR}/sa-destroy.sh
    else
        echo "dk-destroy takes no arguments"
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
