#!/bin/bash

# Use quotes to avoid escaping
cat <<'END'

The dk-* command family supports local development of the AeroFS appliance.
Whereas the production system runs Docker containers in an VM, local
development runs them directly on the host. It allows you to control and
interact with the containers using local docker commands. "DK" stands for
"Development Kit", "DocKer", "DrunK DonKey", or "Denmark", whichever you
prefer.

Prerequisites:
    - understand common docker and crane commands such as 'start', 'kill', 'rm'

Commands:
  dk-create-vm   Set up a VM for doing docker-related things and then run dk-env
  dk-create      build, launch, and configure appliance containers. Previous
                 appliance containers will be destroyed.
  dk-env         Export DOCKER_HOST, DOCKER_CERT_PATH, and DOCKER_TLS_VERIFY
                 into the environment.
  dk-reconfig    Identical to dk-create but it skips image building.
  dk-halt        Stop all appliance containers.
  dk-halt-vm     Stop virtual machine.
  dk-ps          Displays stats about the running containers.
  dk-start       Start halted appliance containers.
  dk-start-vm    Start halted appliance virtual machine.
  dk-restart     Restart all appliance containers.
  dk-destroy     Stop and remove all appliance containers.
  dk-destroy-vm  Additionally destroy the VM running the docker containers
  dk-reload      Remove and restart given containers and all the containers
                 that depend on them.
  dk-exec        Execute commands in a given running container.
  dk-crane       Run `crane` commands using the appliance's crane file at
                 docker/crane.yml

Example:

Follow these steps to start developing and testing an AeroFS service 'foo' from
scratch:

  $ dk-create-vm        # Create a docker-machine VM.
  $ dk-create           # Build and launch the entire appliance. it may take a
                        # while.
  $ make -C src/foo     # Rebuild foo's Docker image after some code change.
  $ dk-reload foo       # Reload the container 'foo' using the newly built.
                        # Image (see docker/crane.yml for container names).
  $ dk-exec foo ps aux  # List all running processes in the container 'foo'.
  $ dk-crane status     # List container status.
  $ dk-ps               # Display stats about all running containers.
  $ dk-halt             # Stop the entire appliance. Use dk-start to restart
                        # it.
  $ dk-destroy-vm       # Wipe the docker-machine VM, if you're running out of
                        # disk space.

END
