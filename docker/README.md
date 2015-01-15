This folder contains Docker specific files, tools, and build scripts. For more 
information, please see [this design doc](../docs/design/docker.md).

# Getting Started

- Install software:

    - [boot2docker](https://docs.docker.com/installation/mac/), and [increase its disk size](https://docs.docker.com/articles/b2d_volume_resize/) to at least 100GB. Building AeroFS
doesn't need this much but it's just in case you become a heavy docker user later :)

    - docker: `brew update && brew install --upgrade docker`
    
    - [crane](https://github.com/michaelsauter/crane#installation)

- Build Docker images:

      $ invoke proto build_client package_clients --mode PRIVATE --unsigned
      $ cd ~/repos/aerofs/docker
      $ make

- Test images:

      $ crane run -d all
      $ open http://`boot2docker ip`

- Build the appliance (after images are built):

      $ cd ~/repos/aerofs/docker
      $ make ship

  Build artifacts including VM images and cloud-config files will be available at ~/repos/aerofs/out.ship.

# Useful links

- [Dockerfile best practices](https://docs.docker.com/articles/dockerfile_best-practices/)

- [Dockerfile IntelliJ plugin](https://github.com/masgari/docker-intellij-idea)

# Ignore docker related files

The docker build system introduced many scripts with the sole purpose of fixing up legacy source code, for example,  replacing "localhost" with "foo.service". These fixes will be removed once we switch away from the legacy system. For the time being, if you want to exclude docker related files from your search results, simply ignore the following file patterns when searching the code base:

- Dockerfile
- Makefile (the legacy system has Makefiles but doesn't use them extensively)
- \*docker\*
- /root/
- /buildroot/

`ag` users may simply place these patterns to their `.agignore` files.
