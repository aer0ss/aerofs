# Design principles and best practices

- Please follow the [12-factor design](http://12factor.net).

- Depending on the situation, the framework may `docker run` a container from 
scratch or `docker start` from an existing container. Therefore, **an app's 
behavior should not dependent on the container's initial ephemeral state**. It 
is okay to depend on state in persistent storage or data volume.

- Listening to the service ports implies that the service is ready to serve 
requests. This is necessary for the *service barrier* to work. As a result, 
before listening to ports, prepare everything the service needs to serve 
requests.

- When possible, mount host folders as subfolders under /host in the container.

# Source folder structure

TODO

# Tips and tricks

Use this command to draw a image dependency graph (requires `brew install 
graphviz`)

    $ docker images --viz | dot -Tpng > graph.png