Hosted Private Cloud Containers
===============================

This folder contains the Docker containers required to create a Hosted Private Cloud server.

Any server that we want to use as a host for Hosted Private Cloud needs to have these containers
running.


Production workflow
===================

The `./make-release` script will build and push these containers to registry.aerofs.com. When we
start a new HPC server on Amazon EC2, we provide a cloud-config file will pull these containers so
that the new server is ready to host new HPC deployments.


Dev workflow
=============

- Use `./setup-local-env` to patch the system so that it works locally.
- Run `make` for each container (hpc-reverse-proxy hpc-port-allocator hpc-docker-gen) to build the
  Docker images.
- Run `crane run` to start the containers on your local Docker VM.
- Run lizard and add the IP of your Docker VM (usually 192.168.99.100) as an HPC server in lizard.
- From there, lizard will be able to launch new HPC deployments on your Docker VM
