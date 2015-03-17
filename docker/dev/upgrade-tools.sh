#!/bin/bash
set -ex

function yellow_print()
{
    (set +x
        YELLOW='1;33'
        cecho() { echo -e "\033[$1m$2\033[0m"; }
        echo
        cecho ${YELLOW} "$@"
        echo
    )
}

if [ -z "$(which vboxmanage)" ]; then
    echo 'Please install VirtualBox first.'
    exit 11
fi

# Mac.
if [ $(uname -s) = "Darwin" ]
then
    # Install docker cli
    brew update && brew install --upgrade docker graphviz

    # Install crane
    bash -c "$(curl -sL https://raw.githubusercontent.com/michaelsauter/crane/master/download.sh)"
    mv crane /usr/local/bin/crane

    # Install docker-machine
    wget https://github.com/docker/machine/releases/download/v0.1.0-rc4/docker-machine_darwin-amd64 \
        -O /usr/local/bin/docker-machine
    chmod +x /usr/local/bin/docker-machine

    # Do not use sudo on mac.
    SUDO=
fi

# Linux.
if [ $(uname -s) = "Linux" ]
then
    # Dev must install crane, docker-machine manually.
    if [ -z "$(which crane)" ] || [ -z "$(which docker-machine)" ]
    then
        echo 'Please install crane and docker-machine manually.'
        exit 22
    fi

    # Sudo is required on linux for certain commands.
    SUDO=sudo
fi

VM=dev

# Install dk-ip
cat > /tmp/dk-ip <<END
#!/bin/sh
docker-machine ip ${VM}
END
${SUDO} mv /tmp/dk-ip /usr/local/bin/dk-ip
${SUDO} chmod +x /usr/local/bin/dk-ip

# Create a docker machine 'dev'
#if [ -z "$(vboxmanage list vms | grep "^\"${VM}\"")" ]
echo "$(vboxmanage list vms)"
if [ -z "$(vboxmanage list vms | grep "${VM}")" ]
then
    docker-machine create -d virtualbox --virtualbox-disk-size 100000 --virtualbox-memory 3072 dev

    # Set environment variables for current and futher bash processes
    $(docker-machine env dev)
    if [ -z "$(grep 'docker-machine env ${VM}' ${HOME}/.bash_profile)" ]
    then
        echo '$(docker-machine env ${VM})' >> "${HOME}/.bash_profile"
    fi

    yellow_print 'Please restart other bash terminals for settings to take effect.'
else
    yellow_print 'Docker-machine vm already created; nothing to do.'
fi
