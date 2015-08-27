# Docker Guide
This is a startup guide to become familiarized with Docker.

## What is Docker?
A simplified application stack looks like the following:

- Application - your code runs here and makes light blink.
- Platform - to provide a programming interface.
- System libraries - to interface with the operating system.
- Operating system - to operate the hardware.
- Hardware - to actuate.

Virtual machines are programs pretending to be hardware and let operating
systems and above work with it. Docker fakes system libraries so that
platforms and applications can run in isolation while reducing resource
consumption.

Note that Docker daemon primarily runs on Linux. On non-Linux platforms, the
most common approach is to have a Linux VM running the Docker daemon.

## Exercise
The below is a series of exercises that walks through the parts from start to
end.

### Docker-Machine
Running Docker on non-Linux platforms requires running VMs. Docker-Machine is a
software to help manage these VMs.

- Run `docker-machine help` to see the list of commands.
- Run `docker-machine ls` to see the list of VMs currently managed by
  Docker-Machine.
- Run
    `docker-machine create
    -d virtualbox
    --virtualbox-disk-size 5000
    --virtualbox-memory 512
    demo`
  to create a VM with 5G of disk and 512MB of RAM named demo.
- Run `docker-machine ssh demo` to ssh into the VM named demo and poke around.
- Poke around in the VM.
- Run `eval "$(docker-machine env demo)"` to configure the docker client
  on your terminal to talk to docker daemon running in the VM named demo.
- Run `printenv | grep -i docker` to see the configuration for the docker
  client.
- Run `docker ps` to ensure the docker client has been successfully configured
  and is talking to the right daemon.

### Docker Lifecycle
- An author writes a Dockerfile describing how to build Docker images. Docker
  images are typically created based off of existing Docker images.
- The user creates a Docker image based on a Dockerfile.
- The user creates a Docker container based on a Docker image and runs the
  container.
- The container can be started, edited, stopped, restarted, and eventually
  destroyed. All file changes in the container stay persistent across
  restarts. The changes are lost when the container is _destroyed_.
- Run `docker help` to see the list of commands.

### Dockerfile
Create a Dockerfile with the following content:

    FROM debian:sid

    MAINTAINER Victory "success@aerofs.com"

    CMD sleep infinity

The above Dockerfile is used to create an image based on Debian sid
distribution and the main process in the container will run the command `sleep
infinity`.

### Docker Images
The idea behind Docker images is to have a baseline setup for a
program/server/service so that one can make many copies and distribute.

- Run `docker build -t test .` to build a Docker image based on the Dockerfile
  in the current directory and tag the image with the name test.
- Run `docker images` to see the list of all images in the local cache. Note
  that you should see the image you've just built as well as many base and
  intermediate images used to build the test image.

### Docker Containers
Now that we have Docker images, we can build and run containers from these
images.

- Run `docker -d --name test-run test` to create a container named test-run
  based on an image named test and run the new container in detached mode.
- The docker client can be attached or detached from a running container.
- When the docker client (terminal) is attached to a running container,
  container outputs are displayed in the terminal and the terminal will wait
  until the main process in the container exits.
- When the docker client is detached, the container runs in the background.
- Run `docker ps` to see the list of _running_ containers. Run `docker ps -a`
  to see the list of all containers running or otherwise.
- Run `docker exec -it test-run bash` to run bash inside the test-run
  container. Note that the container needs to already exist and be running. The
  i and t flags are used to link the terminal running the docker client to the
  terminal environment inside the container so you can execute commands as if
  you are in the container.
- Poke around in the container. Install vim, create a text file and leave a
  message, say `Alex wuz here`.

## How AeroFS uses Docker
AeroFS ships an appliance, either as a VM image or a cloud-config file to
provision an existing VM. From our point of view, we have a VM with docker
daemon installed.

The appliance needs to run a number of services (20-25). Each service runs
inside a container in isolation.

For development, we use `dk-*` commands to create and control a VM running the
Docker daemon. `dk-create` will create a VM named `docker-dev`, build all
service images from the source code, and run all containers from these images.

When the source code changes, the running containers are unaffected. The
developer will need to build new Docker images for the affected containers,
create new containers from those images, and run the new containers.

For details, take a look under `~/repos/aerofs/tools/bashrc/docker.sh` and
`~/repos/aerofs/docker/dev`.
