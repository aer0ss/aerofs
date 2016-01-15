#docker-compose vs Crane
### Pros
0. stated goal of being production ready. crane has always stated that it's designed for use in dev and build environments
0. native support and preference of docker networks, which will obsolete the links that crane uses
	- this will solve our issues with restarting containers and service discovery
	- one container going down will not halt all the containers that depend on it from starting
	- can restart a subset of containers without needing to restart everything
0. officially supported by docker, developed more actively
0. stated goals of being able to run across docker swarm and scale services to multiple containers, could be useful in the multi-appliance scalability phase
0. more capabilities handled inside of docker compose, instead of our current combination of python server and bash scripts
0. can support containers that are not managed explicitly by compose, e.g. loader
0. possibly will automatically carry over volumes from past containers even if the version is different, meaning that we no longer need to copy volume data over manually (slow) on in-place upgrade (!)

### Cons
0. no support of container groups, will have to split out the different groups into different compose files, however does support importing from another file - so there isn't any need for duplication
0. by moving to docker network, we can no longer use links as our explicit dependency declaration, it will have to be moved to our service-barrier script
0. migration with in-place upgrade from crane to loader may be tricky
0. docker networks don't have the concept of setting hostnames explicitly, so we'll need to change our hostnames from `http://web.service/path/to/object` to `http://web/path/to/object` (the solution to this is already being actively worked on, may be out in docker 1.10)
