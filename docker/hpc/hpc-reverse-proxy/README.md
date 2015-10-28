Hosted Private Cloud Reverse Proxy
==================================

On Hosted Private Cloud, several appliances are running on the same physical machine. The role of
this reverse proxy is to route the HTTP and HTTPS traffic to the correct appliance.

To do so, we use [docker-gen](https://github.com/jwilder/docker-gen) to listen for new containers
and update the nginx config accordingly.


Running locally
---------------

hpc-reverse-proxy is configured under the assumption that it will be routing traffic on the
aerofs.com domain. You can use the `setup-local-env.sh` script to make it work with the syncfs.com
domain, with self-signed SSL certificates.

For more information on how to run HPC locally, including hpc-reverse-proxy, please refer to the
README in the parent directory.

