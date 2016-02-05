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

- Turn off all of your docker containers using a dk-halt.
- Use `./setup-local-env` to patch the system so that it works locally.
- Run `make` for each HPC container (hpc-reverse-proxy hpc-port-allocator hpc-docker-gen
  hpc-logrotator) to build the Docker images.
- Run `docker pull registry.aerofs.com/aerofs/hpc-sail`
- Run `crane run` to start the HPC containers on your local Docker VM.
- Run lizard and add the IP of your Docker VM (usually 192.168.99.100) and the
  docker url ( https://<docker-ip>:2376) as an HPC server in lizard.
- From there, lizard will be able to launch new HPC deployments on your Docker VM.


Creating Deployments
====================

- The code that contains how new HPC Deployments are created is located in
  `~/repos/aerofs/src/lizard/lizard/hpc.py` and `~/repos/aerofs/src/lizard/lizard.hpc_config.py`
- Follow the `README.md` in `~/repos/aerofs/src/lizard` to run your celery tasks which will
  enable you to create a new deployment.
