#!/bin/bash
set -ex

if [ -z "$(which vboxmanage)" ]; then
    echo 'Please install VirtualBox first.'
    exit 11
fi

# Install docker cli
brew update && brew install --upgrade docker graphviz

# Install crane
bash -c "$(curl -sL https://raw.githubusercontent.com/michaelsauter/crane/master/download.sh)"
mv crane /usr/local/bin/crane

# Install docker-machine
wget https://github.com/docker/machine/releases/download/v0.1.0-rc4/docker-machine_darwin-amd64 \
    -O /usr/local/bin/docker-machine
chmod +x /usr/local/bin/docker-machine

VM=dev

# Install dk-ip
cat > /usr/local/bin/dk-ip <<END
#!/bin/sh
docker-machine ip ${VM}
END
chmod +x /usr/local/bin/dk-ip

# Create a docker machine 'dev'
if [ -z "$(vboxmanage list vms | grep "^\"${VM}\"")" ]; then
    docker-machine create -d virtualbox --virtualbox-disk-size 100000 --virtualbox-memory 3072 dev

    # Set environment variables for current and futher bash processes
    $(docker-machine env dev)
    if [ -z "$(grep 'docker-machine env ${VM}' ${HOME}/.bash_profile)" ]; then
        echo '$(docker-machine env ${VM})' >> "${HOME}/.bash_profile"
    fi

    (set +x
        YELLOW='1;33'
        cecho() { echo -e "\033[$1m$2\033[0m"; }
        echo
        cecho ${YELLOW} 'Please restart other bash terminals for settings to take effects'
        echo
    )
fi
