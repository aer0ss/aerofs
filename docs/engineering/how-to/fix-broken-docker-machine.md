How To Fix a Broken docker-machine
==================================

Every once in a while something goes wrong on your docker machine (the VM where the Docker daemon is
running) and you get this error message:

    Error running connection boilerplate: Error checking and/or regenerating the certs: There was an error validating certificates for host "192.168.99.100:2376": dial tcp 192.168.99.100:2376: getsockopt: connection refused
    You can attempt to regenerate them using 'docker-machine regenerate-certs name'.
    Be advised that this will trigger a Docker daemon restart which will stop running containers.

When this happens, it's usually not because of certificate issues as the error message suggests, but
because your Docker daemon failed to start. You could fix the issue by destroying and recreating the
VM (dk-destroy-vm / dk-create-vm) but it's a waste of time. Instead, you can usually fix this in a
couple of minutes.  Here's how to troubleshoot:


1. Make sure you're connected to the VPN.


2. Restart your docker-machine:

    $ docker-machine restart docker-dev
    $ dk-env

If `dk-env` succeeds, you should be good to go. Otherwise proceed to the next step.


3. SSH into the box to see what's going on:

    $ docker-machine ssh docker-dev
    $ ps aux | grep docker

Do you see the docker daemon here? Something like `/usr/local/bin/docker daemon [...]`. You most
likely won't be seeing it, because it failed to start for some reason. Do you see any other Docker-
related thing going on? If so, `sudo kill -9 <pid>` to kill any stray Docker process


4. A common cause of trouble is networking configuration changes (which happen when connecting in
   and out of the VPN) that are badly handled by Docker/VirtualBox. To fix:

   docker@docker-dev:~$ sudo rm -rf /var/lib/docker/network


5. Try running the Docker daemon manually:

    docker@docker-dev:~$ sudo docker daemon

If you get any error message, try to understand and fix it. A good way to fix things is to do
`docker ps -a` and `docker rm --force` any container that you have. (Do that in another SSH session
so that you can keep this one running with the Docker daemon log) Otherwise, if it's all blue [INFO]
logs, you should be good. Exit the box and restart it one more time: $ docker- machine restart
docker-dev
