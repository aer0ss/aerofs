#!/bin/bash

# Use quotes to avoid escaping
cat <<'END'

The dk-* command family supports local development of the AeroFS appliance. Whereas the production system runs Docker
containers in an VM, local development runs them directly on the host. It allows you to control and interact with the
containers using local docker commands. "DK" stands for "Development Kit", "DocKer", "DrunK DonKey", or "Denmark",
whichever you prefer.

Prerequisites:
    - understand common docker and crane commands such as 'start', 'kill', 'rm'
    - run upgrade-tools.sh and restart bash before using dk-* commands.

Commands:
    dk-createvm  set up a VM for doing docker-related things and then run dk-env
    dk-create    build, launch, and configure appliance containers. Previous appliance containers will be destroyed.
    dk-env       export DOCKER_HOST, DOCKER_CERT_PATH, and DOCKER_TLS_VERIFY into the environment
    dk-reconfig  identical to dk-create but it skips image building
    dk-halt      stop all appliance containers
    dk-start     start halted appliance containers
    dk-restart   restart all appliance containers
    dk-destroy   stop and remove all appliance containers
    dk-destroyvm additionally destroy the VM running the docker containers
    dk-reload    remove and restart given containers and all the containers that depend on them
    dk-exec      execute commands in a given running container
    dk-crane     run `crane` commands using the appliance's crane file at docker/crane.yml

Example:

Follow these steps to start developing and testing an AeroFS service 'foo' from scratch:

    $ docker/dev/upgrade-tools.sh # install required tools
    $ dk-createvm                 # create a docker-machine VM
    $ dk-create                   # build and launch the entire appliance. it may take a while.
    $ make -C src/foo             # rebuild foo's Docker image after some code change
    $ dk-reload foo               # reload the container 'foo' using the newly built image (see docker/crane.yml for container names)
    $ dk-exec foo ps aux          # list all running processes in the container 'foo'
    $ dk-crane status             # list container status
    $ dk-halt                     # stop the entire appliance. Use dk-start to restart it
    $ dk-destroyvm                # wipe the docker-machine VM, if you're running out of disk space

END
