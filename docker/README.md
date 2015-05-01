This folder contains Docker specific files, tools, and build scripts. For more 
information, please see [this design doc](../docs/design/docker.html).

# Get started with development environtment

1. Install build tools. You may run this script again at any time to upgrade the tools.

        $ ~/repos/aerofs/docker/dev/upgrade-tools.sh

2. Build and launch the appliance. The first run takes about 40 minutes. Follow-up instructions
is printed at the end of the process. [Learn Docker](https://docs.docker.com/userguide/) while
it's in progress.

        $ dk-create

3. Now you can rebuild and reload individual service containers.

        $ make -C src/bifrost && dk-reload bifrost

4. Learn more about the dk command family using:

        $ dk-help

# Build appliance VM image

        $ docker/ship-aerofs/build-vm.sh
        
This builds the preloaded VM (See Ship Enterprise docs). The path to the generated
file is printed at the end of the process.

# Build appliance cloud-config file

        $ docker/ship-aerofs/build-vm.sh
        
This builds the cloud-config file (See Ship Enterprise docs). The path to the generated
file is printed at the end of the process.

# Tips and tricks

## Reading the logs

You can use this to read the logs of a given container:

    docker logs <name-of-container>
    # For example
    docker logs loader

## Debugging a crashed container

Let's imagine this could possibly happen. Let's imagine the `nginx` container just crashed,
and `docker logs nginx` doesn't give you satisfying elements. Here is what you can do:

    docker commit nginx <new_image_of_crashed_nginx>
    docker run --rm --it <new_image_of_crashed_nginx> bash

Replace `<new_image_of_crashed_nginx>` by any valid name, like `nginx_crashed`.

## Dependency graph

Show the container dependency graph, with Bifrost's link dependency highlighted:

    $ crane graph -dlink bifrost | dot -Tpng > /tmp/containers.png && open /tmp/containers.png

## Image graph

Show the dependency graph of all local Docker images:

    $ docker images --viz | dot -Tpng > /tmp/images.png && open /tmp/images.png

## Useful links

- [Dockerfile best practices](https://docs.docker.com/articles/dockerfile_best-practices/)

- [Dockerfile IntelliJ plugin](https://github.com/masgari/docker-intellij-idea)
