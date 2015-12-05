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
if [ $(uname -s) = "Darwin" ] ; then
    # Install docker cli
    brew update && brew install --upgrade docker graphviz

    # Install crane
    bash -c "$(curl -sL https://raw.githubusercontent.com/michaelsauter/crane/v1.1.1/download.sh)"
    mv crane /usr/local/bin/crane

    # Install docker-machine
    curl -L https://github.com/docker/machine/releases/download/v0.5.0/docker-machine_darwin-amd64.zip \
        > docker-machine.zip
    unzip docker-machine.zip
    rm docker-machine.zip
    mv -f docker-machine* /usr/local/bin/
fi

# Linux.
if [ $(uname -s) = "Linux" ] ; then
    # Experimental!
    if [ -z "$(which docker)" ] ; then
        # Note: as of today, the docker package in the repositories is not up to date (1.2 but we need at least 1.7)
        # sudo apt-get install docker.io
        # So we're going to install it from the PPA
        sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 36A1D7869245C8950F966E92D8576A8BA88D21E9
        sudo sh -c "echo deb https://get.docker.com/ubuntu docker main > /etc/apt/sources.list.d/docker.list"
        sudo apt-get update
        sudo apt-get install lxc-docker

        if [ -z "$(groups | grep docker)" ]
        then
            sudo groupadd docker || echo "The docker group already exists..."
            sudo gpasswd -a ${USER} docker
            sudo service docker restart
            echo "You have been added to the 'docker' group. You should now log out and log in."
            exit 22
        fi
    fi
    if [ -z "$(which crane)" ] ; then
        bash -c "$(curl -sL https://raw.githubusercontent.com/michaelsauter/crane/v1.5.1/download.sh)" && sudo mv crane /usr/local/bin/crane
    fi
    if [ -z "$(which docker-machine)" ] ; then
        curl -L https://github.com/docker/machine/releases/download/v0.5.0/docker-machine_linux-amd64.zip \
            > docker-machine.zip && unzip docker-machine.zip && rm docker-machine.zip && \
            sudo mv docker-machine* /usr/local/bin/
    fi
    if [ -n "$(make -v | grep 'GNU Make 4.')" ] ; then
        yellow_print "WARNING : Make 4.00 is the default version for new Ubuntu installations, but we only support Make 3.8.
      The easiest way to solve this problem is to download and install make-3.81:

wget http://mirrors.kernel.org/ubuntu/pool/main/m/make-dfsg/make_3.81-8.2ubuntu3_amd64.deb -O make_3.81.deb
sudo dpkg -i make_3.81.deb && rm make_3.81.deb"
        exit 22
    fi

    # We need python + jinja2 to process crane.yml.jinja
    pip install jinja2
fi

yellow_print "Docker tools updated.  Run 'dk-create-vm' now if you haven't created a docker-machine VM, or just 'dk-env' to use the rest of the dk-* toolchain."
