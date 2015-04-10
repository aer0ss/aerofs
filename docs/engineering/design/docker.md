# Dockerize the AeroFS Appliance

*[ Work in progress ]*

Legend: All micro-service names are in CamelCase.

## High-level design

- Use [`crane`](https://github.com/michaelsauter/crane) to define container 
dependencies and inter-networking
    - It can be used for development environments, too. No more local prod.
    - When it's time to decompose the appliance, replace crane with a 
distributed [orchestration 
framework](https://github.com/weihanwang/docker-ecosystem-survey#orchestration-f
rameworks-ie-paas-software-for-docker).
- Services refer to other services using hostnames in /etc/hosts and static 
ports.
    - We should not use Docker provided environment variables. 
[Here](http://www.fig.sh/env.html) is why.
    - We should not dynamically allocate ports. 
[Here](http://youtu.be/YrxnVKZeqK8?t=14m31s) is why.
- Use `docker run --restart=always` combined with `loader/boot` API to reload containers?
- Use a [data-only container 
pattern](http://container42.com/2013/12/16/persistent-volumes-with-docker-contai
ner-as-volume-pattern/) for persistent data.

## Filesystem mutations

In general, all file-system based inter-service communications should be 
replaced with REST APIs.

Docker data volumes are an alternative to APIs. It's less desirable because 1) 
it doesn't decouple services as well as APIs, and 2) volume management is not 
well supported as of Dec 2014. See 
[here](https://github.com/cpuguy83/docker-volumes), 
[here](http://container42.com/2014/11/03/docker-indepth-volumes/), and 
[here](http://container42.com/2014/11/18/data-only-container-madness/).

In the first iteration I opt to use data volumes. In 
the future most volume data should be replaced with external data stores for 
easier replication, backup, and horizontal scalability.

See also: [The 12-factor app](http://12factor.net).

## PREDOCKER comments

The structure of our current codebase is unfriendly to Docker build. 
There are many instructions tagged with the "PREDOCKER" keyword to prepare and fix up files before building docker images. We should eventually reorganize the code and remove PREDOCKER comments.


## If you like Sci-Fi movies
    
- Eventually Config should be replaced by [`etcd`](https://coreos.com/docs/- 
distributed-configuration/getting-started-with-etcd/), because:
    1. Similar to Bootstrap, Config is a meta service and shouldn't operate at 
the container level.
    2. etcd supports HA and replication.
    3. etcd's file-system like hierarchical API allows much better organization 
of config variables.
    4. etcd supports authentication and ACL.
    5. Services can subscribe to configurations change notifications.
    6. etc has been battle-field tested and widely adopted by the community.

- Kubernetes concepts of pods, services, replication controllers, & 
`createExternalLoadBalancer` are neat. Apply them to AeroFS?
