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
    # Experimental!
    if [ -z "$(which crane)" ] || [ -z "$(which docker)" ]
    then
        # Note: as of today, the docker package in the repositories is not up to date (1.2 but we need at least 1.3)
        # sudo apt-get install docker.io
        # So we're going to install it from the PPA
        sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 36A1D7869245C8950F966E92D8576A8BA88D21E9
        sudo sh -c "echo deb https://get.docker.com/ubuntu docker main > /etc/apt/sources.list.d/docker.list"
        sudo apt-get update
        sudo apt-get install lxc-docker

        bash -c "`curl -sL https://raw.githubusercontent.com/michaelsauter/crane/master/download.sh`" && sudo mv crane /usr/local/bin/crane

        wget https://github.com/docker/machine/releases/download/v0.1.0/docker-machine_linux-amd64 \
        -O docker-machine && sudo mv docker-machine /usr/local/bin/docker-machine
        sudo chmod +x /usr/local/bin/docker-machine

        if [ -z "$(make -v | grep 'GNU Make 4.0')" ]
        then
            yellow_print "WARNING : Make 4.00 is the default version for new Ubuntu installations, but we only support Make 3.8.
          The easiest way to solve this problem is to download and install make-3.81:

    wget http://mirrors.kernel.org/ubuntu/pool/main/m/make-dfsg/make_3.81-8.2ubuntu3_amd64.deb -o make_3.81.deb
    sudo dpkg -i make_3.81.deb && rm make_3.81.deb"
            exit 22
        fi

        if [ -z "$(groups | grep docker)" ]
        then
            sudo groupadd docker || echo "The docker group already exists..."
            sudo gpasswd -a ${USER} docker
            sudo service docker restart
            echo "You have been added to the 'docker' group. You should now log out and log in."
            exit 22
        fi
    fi

    # Sudo is required on linux for certain commands.
    SUDO=sudo
fi

VM=docker-dev

# Install dk-ip
cat > /tmp/dk-ip <<END
#!/bin/sh
docker-machine ip ${VM}
END
${SUDO} mv /tmp/dk-ip /usr/local/bin/dk-ip
${SUDO} chmod +x /usr/local/bin/dk-ip

# Create a docker machine 'docker-dev'
#if [ -z "$(vboxmanage list vms | grep "^\"${VM}\"")" ]
echo "$(vboxmanage list vms)"
if [ -z "$(vboxmanage list vms | grep "${VM}")" ]
then
    docker-machine create -d virtualbox --virtualbox-disk-size 100000 --virtualbox-memory 3072 ${VM}

    if [ -z "$(grep 'docker-machine env ${VM}' ${HOME}/.bash_profile)" ]
    then
        echo "\$(docker-machine env ${VM})" >> "${HOME}/.bash_profile"
    fi

    yellow_print 'Please restart this and all other bash terminals for settings to take effect.'
else
    yellow_print 'Docker-machine vm already created; nothing to do.'
fi
