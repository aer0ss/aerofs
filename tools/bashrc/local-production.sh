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

function lp-kick-transient()
{
    cd $AEROFS_ROOT/packaging/bakery/developer
    ./lp-kick.sh app-transient
}

function lp-kick-persistent()
{
    cd $AEROFS_ROOT/packaging/bakery/developer
    ./lp-kick.sh app-persistent
}

function lp-kick-all()
{
    cd $AEROFS_ROOT/packaging/bakery/developer
    ./lp-kick.sh app-all
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

# -----------------------------------------------------------
# Deploys
# -----------------------------------------------------------

function lp-deploy-all()
{
    _lp-package-all
    lp-kick-transient
}

function lp-deploy-bootstrap()
{
    _lp-package-bootstrap
    lp-kick-all
}

function lp-deploy-web()
{
    _lp-package-web
    lp-kick-transient
}

function lp-deploy-sp()
{
    _lp-package-sp
    lp-kick-transient
}

function lp-deploy-verkehr()
{
    _lp-package-verkehr
    lp-kick-transient
}

function lp-deploy-zephyr()
{
    _lp-package-zephyr
    lp-kick-transient
}

function lp-deploy-ca()
{
    _lp-package-ca
    lp-kick-persistent
}

# -----------------------------------------------------------
# VM Controls
# -----------------------------------------------------------

function lp-vmctl-setup()
{
    _lp-package-all
    cd $AEROFS_ROOT
    ant local_prod_setup -Dbin=$(_lp-bin)
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
    ant local_prod_start
}
