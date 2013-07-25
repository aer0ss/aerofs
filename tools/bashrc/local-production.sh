#!/bin/bash
#
# Local Production Aliases.
#
# In this file, lp is short for local prodcution. Aliases that are meant to be
# used publicly are prefixed with "lp-" for easy bash completion.

function _lp-repo()
{
    whoami | tr [a-z] [A-Z]
}

#
# Execute a command in a specified directory
# Changes back to the original directory once the command
# completes and returns the exit code
#
# Arguments:
#    $1   : directory to change to
#    $2..N: command and arguments to run in $1
#
function DoIn
{
    typeset _wd=$PWD
    cd $1
    shift
    set +e

    $@;

    typeset retval=$?
    cd $_wd

    #FIXME: set -e only if $- had an e in it
    #set -e
    return $retval
}

function _lp-vm-cmd()
{
    DoIn $AEROFS_ROOT/packaging/bakery/developer $@
}

# -----------------------------------------------------------
# Packaging
# -----------------------------------------------------------

function _lp-package-cmd()
{
    DoIn $AEROFS_ROOT ant clean -Drepo=$(_lp-repo) $@
}

function _lp-package-all()
{
    _lp-package-cmd upload_servers -Dproduct=CLIENT -Dmode=PROD
}

function _lp-package-bootstrap()
{
    _lp-package-cmd upload_bootstrap -Dproduct=CLIENT -Dmode=PROD
}

function _lp-package-sanity()
{
    _lp-package-cmd upload_sanity
}

# -----------------------------------------------------------
# Kicks
# -----------------------------------------------------------

function _lp-kick-transient()
{
    _lp-vm-cmd kick.sh transient
}

function _lp-kick-persistent()
{
    _lp-vm-cmd kick.sh persistent
}

function _lp-kick-all()
{
    _lp-vm-cmd kick.sh
}

# -----------------------------------------------------------
# Deploys
# -----------------------------------------------------------

function lp-deploy-all()
{
    _lp-package-all
    _lp-kick-transient
}

function lp-deploy-bootstrap()
{
    _lp-package-bootstrap
    _lp-kick-all
}

function lp-deploy-sanity()
{
    _lp-package-sanity
    _lp-kick-all
}

# -----------------------------------------------------------
# Virtual Machines
# -----------------------------------------------------------

function lp-setup()
{
    if [[ $# -eq 1 ]]
    then
        interface_arg=$1
    else
        interface_arg='$(./interfaces.sh)'
    fi

    _lp-package-all
    _lp-vm-cmd setup.sh $(_lp-repo) ${interface_arg}
}

function lp-start()
{
    if [[ $# -eq 1 ]]
    then
        interface_arg=$1
    else
        interface_arg='$(./interfaces.sh)'
    fi

    _lp-vm-cmd start.sh ${interface_arg}
}

function lp-stop()
{
    _lp-vm-cmd halt.sh
}

function lp-delete()
{
    _lp-vm-cmd delete.sh
}

# -----------------------------------------------------------
# SSH
# -----------------------------------------------------------

function _lp-ssh-usage()
{
    echo "Usage: lp-ssh <host>"
    echo
    echo "Available hosts:"
    echo " - persistent"
    echo " - transient"
    echo
    echo "Simply sudo su to get root access (no password required)."
}

function lp-ssh()
{
    if [ $# -ne 1 ]
    then
        _lp-ssh-usage
        return
    fi

    local target=
    case $1 in
        persistent)
            target=persistent
            ;;
        transient)
            target=transient
            ;;
    esac

    if [ -z "$target" ]
    then
        _lp-ssh-usage
        return
    fi

    DoIn $AEROFS_ROOT/packaging/bakery/vagrant ./ssh.sh $target
}
