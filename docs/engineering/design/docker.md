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
- Use `docker run --restart=always` combined with `<service>/reload` API to 
allow Bootstrap to reload containers.
    - May need to patch fig to [add restart 
support](https://github.com/docker/fig/issues/478)
    - Perhaps Bootstrap is not needed at all. Can we simply `fig stop && fig 
start`?
- Use the [data-only container 
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

In the first iteration I opt to use data volumes for faster implementation. In 
the future most volume data should be replaced with external data stores for 
easier replication, backup, and horizontal scalability.

See also: [The 12-factor app](http://12factor.net).

## PREDOCKER comments

The structure of our current codebase is unfriendly to Docker build. 
There are many instructions tagged with the "PREDOCKER" keyword to prepare and fix up files before building docker images. We should eventually reorganize the code and remove PREDOCKER comments.


## To-do List

The following tasks are necessary to completely containerize AeroFS services. 
Even if we end up with not adopting Docker, these changes would improve service 
decoupling and pave the road for scaling the appliance in the future. 

- reliably delete files from buildroot using a master buildroot. Use plain copying to populate buildroot and then lazy-rsync (rsync with lazy-copy) to sync to target buildroot.

- Update osx-repackaging source code.

- Use stable rather than alpha CoreOS release channel (by the time of writing 
stable doesn't support gz+b64 cloud-config encoding.

- Avoid buildpack-deps. They're huge. And optimize image sizes.

- Expose docker pull progress.

- following bootstrap tasks: 
ejabberd-metadata-reset
ejabberd-fix-config
ejabberd-certify
update-monitoring-password
bifrost-sanity-check
sanity-check

- persist browser cert

- bug: http://share.syncfs.com/create_first_user?email=weihan%40aerofs.com 
redirects to 
https://share.syncfs.com/create_first_user?email=weihan%40aerofs.com?email=weiha
n%40aerofs.com

- service barrier

- license expiry should kill services

- on launching, wait for all links

- vk calls Identity to retrieve CRL on launch.

- use uwsgi rather than pserve for web?

- implement bootstrap task reconfigure-ntp

- redis: how to run "sysctl vm.overcommit_memory = 1"?

- maintenance mode

- restore & backup

- remove url_ignore_status.sh and implement proper sanity checks

- enforce_license: aerofs/config should expose an API to return whether the 
license has expired. The client containers (verkehr & zephyr) should 
periodically check the API and shut down the service if license expired. i.e. 
move enforce_license logic from 
`puppetmaster/modules/persistent/manifests/services.pp` to client containers.

- Bootstrap: migrate the following remaining tasks to containers. Some of them 
might be no longer needed in the new system:
    - nginx-certificate-generate-placeholder
    - browser-cert-populate
    - enable-all-nginx-services

- Stop using /etc/ssl/certs/AeroFS_CA.pem and call certifier/certify to 
retrieve the file and place it in /opt/<service>/cacert.pem. Also, update 
certifier/certify to call CA's API rather than reading from the data volume.

- Every time a service launches it may request a new cert from CA. We should 
clean up CA's certs directory every time CA starts up.

- TODOs in docker/data-container/README.md

- Use build containers to compile things up, so the host dev environment can be 
completely clean.

- Some images may be unofficial builds. We should only use official images.

- Before releasing to production, verify all the code/scripts that duplicate 
with the original source are still consistent with the original. In particular, 
check all nginx configurations.

- Remove the `docker=1` configuration property and related code.

- use buildpack-deps:micro instead of buildpack-deps

- TS sanity check? Change-Id: Ic70edc0ce09096d67d2bd0a2ec083895d49d407d



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