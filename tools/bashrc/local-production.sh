#!/bin/bash
#
# Local Production Aliases.
#
# In this file, lp is short for local prodcution. Aliases that are meant to be
# used publicly are prefixed with "lp-" for easy bash completion.

function _lp-bin()
{
    whoami | tr [a-z] [A-Z]
}

# -----------------------------------------------------------
# Kicks
# -----------------------------------------------------------

function _lp-kick-transient()
{
    cd $AEROFS_ROOT/packaging/bakery/developer
    ./kick.sh app-transient
}

function _lp-kick-database()
{
    cd $AEROFS_ROOT/packaging/bakery/developer
    ./kick.sh database
}

function _lp-kick-persistent()
{
    cd $AEROFS_ROOT/packaging/bakery/developer
    ./kick.sh app-persistent
}

function _lp-kick-all()
{
    cd $AEROFS_ROOT/packaging/bakery/developer
    ./kick.sh
}

# -----------------------------------------------------------
# Packaging
# -----------------------------------------------------------

function _lp-package-all()
{
    cd $AEROFS_ROOT
    ant package_servers -Dbin=$(_lp-bin) -Dproduct=CLIENT -Dmode=PROD
    cd packaging
    BIN=$(_lp-bin) make upload
}

function _lp-package-bootstrap()
{
    cd $AEROFS_ROOT
    ant package_bootstrap -Dbin=$(_lp-bin) -Dproduct=CLIENT -Dmode=PROD
    cd packaging
    BIN=$(_lp-bin) make upload
}

function _lp-package-web()
{
    cd $AEROFS_ROOT/packaging
    BIN=$(_lp-bin) make clean common web upload
}

function _lp-package-installers()
{
    cd $AEROFS_ROOT/packaging
    BIN=$(_lp-bin) make installers upload
}

function _lp-package-sp()
{
    cd $AEROFS_ROOT
    ant package_sp -Dbin=$(_lp-bin) -Dproduct=CLIENT -Dmode=PROD
    cd packaging
    BIN=$(_lp-bin) make upload
}

function _lp-package-verkehr()
{
    cd $AEROFS_ROOT
    ant package_verkehr -Dbin=$(_lp-bin) -Dproduct=CLIENT -Dmode=PROD
    cd packaging
    BIN=$(_lp-bin) make upload
}

function _lp-package-zephyr()
{
    cd $AEROFS_ROOT
    ant package_zephyr -Dbin=$(_lp-bin) -Dproduct=CLIENT -Dmode=PROD
    cd packaging
    BIN=$(_lp-bin) make upload
}

function _lp-package-ca()
{
    cd $AEROFS_ROOT/packaging
    BIN=$(_lp-bin) make clean ca-tools ca-server upload
}

function _lp-package-sanity()
{
    cd $AEROFS_ROOT/packaging
    BIN=$(_lp-bin) make clean sanity upload
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

function lp-deploy-web()
{
    _lp-package-web
    _lp-kick-transient
}

function lp-deploy-installers()
{
    _lp-package-installers
    _lp-kick-transient
}

function lp-deploy-sp()
{
    _lp-package-sp
    _lp-kick-transient
}

function lp-deploy-verkehr()
{
    _lp-package-verkehr
    _lp-kick-transient
}

function lp-deploy-zephyr()
{
    _lp-package-zephyr
    _lp-kick-transient
}

function lp-deploy-ca()
{
    _lp-package-ca
    _lp-kick-persistent
}

function lp-deploy-sanity()
{
    _lp-package-sanity
    _lp-kick-all
}

# -----------------------------------------------------------
# VM Controls
# -----------------------------------------------------------

function _lp-vmctl-setup()
{
    cd $AEROFS_ROOT
    if [ "$#" -eq "1" ] ; then
        ant local_prod_setup -Dbin=$(_lp-bin) -Dbridge_iface=$1
    else
        ant local_prod_setup -Dbin=$(_lp-bin)
    fi
}

function lp-vmctl-setup()
{
    _lp-package-all
    _lp-vmctl-setup "$@"
}

function lp-vmctl-delete()
{
    cd $AEROFS_ROOT
    ant local_prod_delete
}

function lp-vmctl-halt()
{
    cd $AEROFS_ROOT
    ant local_prod_halt
}

function lp-vmctl-start()
{
    cd $AEROFS_ROOT
    if [ "$#" -eq "1" ] ; then
        ant local_prod_start -Dbridge_iface=$1
    else
        ant local_prod_start
    fi
}

# -----------------------------------------------------------
# SSH
# -----------------------------------------------------------

function _lp-ssh-usage()
{
    echo "Usage: lp-ssh <host>"
    echo
    echo "Available hosts:"
    echo " - ca|config|app-persistent"
    echo " - admin|app-transient"
    echo " - database"
    echo
    echo "Simply sudo su to get root access - no password required."
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
        ca)
            target=app-persistent
            ;;
        config)
            target=app-persistent
            ;;
        app-persistent)
            target=app-persistent
            ;;
        admin)
            target=app-transient
            ;;
        app-transient)
            target=app-transient
            ;;
        database)
            target=database
            ;;
    esac

    if [ -z "$target" ]
    then
        _lp-ssh-usage
        return
    fi

    cd $AEROFS_ROOT/packaging/bakery/vagrant
    ./ssh.sh $target
}
