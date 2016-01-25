Package caching for docker builds
=================================

Problems
--------

 - apt index download from upstream is slow (and sometimes unreliable)
   and this is magnified by the fact that many base containers discard
   the apt index to save space
 - package downloads from upstream is slow (and sometimes unreliable)
 - many containers have large intersections of package requirements
   resulting in duplicate downloads


Solution
--------

 Use local package caches


Design
------

The package caching infrastructure is fully containerized. The main
components are as follows:

 - rawdns: This container provides a configurable DNS resolver which
   is set as the default resolver used by the docker daemon. Ideally
   this would only be used by `docker build`, unfortunately docker
   currently doesn't have a provision to override the resolver at build
   time only and we have to tweak the docker daemon config, which also
   affects appliance containers.
   
 - apt-cacher-ng: a containerized instance of the well-know caching apt
   proxy server. rawdns performs transparent redirection of standard
   debian and ubuntu apt repos to this container, ensuring Dockerfiles
   remain blissfully unaware of the presence of the proxy.

 - alpinx: a containerized instance of nginx configured as a caching
   reverse proxy for Alpine Linux package repository. rawdns performs
   transparent redicrection to this container, ensuring Dockerfiles
   remain blissfully unaware of the presence of the proxy.

 - devpi: a containerized instance of a caching pypi proxy server.
   Unfortunately, pip is built in such a way that transparent proxying
   is impossible, which means Dockerfiles have to be modified to point
   to the package index provided by this container. rawdns allows this
   configuration tweak to refer to the container as devpi.docker instead
   of having to somehow extract the container ip in a fragile way.


Integration in build system
---------------------------

The top-level `start.sh` script is automatically called by the container
build script, to ensure that caching is enabled as early as possible.


Troubleshooting
---------------

A stop script is provided to disable the caching infrastructure in case
it somehow gets into a bad state. It is however recommended to use the
more specific `rawdns/stop.sh` for two reasons:
  - apt-cacher-ng and devpi are fairly unlikely to get into a bad state
  - apt-cacher-ng has been known to fail to stop cleanly, resulting in
    unkillable containers. This is a known docker issue, supposedly fixed
    in 1.7, unfortunately 1.7 is known to somehow fail to run both rawdns
    and apt-cacher-ng containers for obscure reasons.

The most fragile piece of the caching infrastructure is the somewhat hacky
way in which the docker daemon configuration is tweaked to use the rawdns
container as the default DNS resolver.

In a docker-machine/boot2docker environment, which is the default (and only
officially supported) dev environment this is achieved by editing the file
`/var/lib/boot2docker/profile` to add `--dns 172.17.42.1` to the `EXTRA_ARGS`
variable which gets passed to the docker daemon by the init script.

Unfortunately, the docker init script in boot2docker is pretty badly broken
so `restart` is unusable. Besides, `stop` doesn't block until the daemon
actually stops, which is currently worked around by adding an explicit wait
before trying to `start` the dameon again.

Environments using raw docker cannot be easily supported because tweaking the
docker daemon config will be platform-dependent and most likely requires root
access. User can manually perform this tweak but need to be careful to do it
after the rawdns container is up and running, otherwise it will fail to build.


TODO
----

 - Improve reliability of docker daemon restart
 - Allow rawdns container to be built even with broken DNS config (vendor
   rawdns sources to avoid github clone)
 - Get docker to support build-time `--dns` settings to remove the need for
   persistent docker daemon config tweak
 - npm cache

