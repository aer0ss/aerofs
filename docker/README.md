This folder contains Docker specific files, tools, and build scripts. For more 
information, please see [this design doc](../docs/design/docker.html).

# Get started

1. Install build tools. You may run this script again at any time to upgrade the tools.

        $ ~/repos/aerofs/docker/dev/upgrade-tools.sh

2. Restart the bash terminal for changes to take effect.
   
3. Build and launch the appliance. The first run takes about 30 minutes. Follow-up instructions
is printed at the end of the process. [Learn Docker](https://docs.docker.com/userguide/) while
it's in progress.

        $ dk-create

4. Now you can rebuild and reload individual service containers.

        $ make -C src/bifrost && dk-reload bifrost
        
5. Learn more about the dk command family using:

        $ dk-help

# Build appliance VM (optional)

You do NOT need the VM for most development work. Follow the commands below to build appliance
VM images. The output file's location is printed at the end of this step.

        $ dk-create && make -C docker ship


# Tips and tricks

## Dependency graph

Show the container dependency graph, with Bifrost's link dependency highlighted:

    $ crane graph -dlink bifrost | dot -Tpng > /tmp/containers.png && open /tmp/containers.png
    
## Image graph

Show the dependency graph of all local Docker images:

    $ docker images --viz | dot -Tpng > /tmp/images.png && open /tmp/images.png

## Useful links

- [Dockerfile best practices](https://docs.docker.com/articles/dockerfile_best-practices/)

- [Dockerfile IntelliJ plugin](https://github.com/masgari/docker-intellij-idea)
