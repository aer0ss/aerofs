# Hosted Private Cloud Design

## Abstract

We want to be able to automatically provision AeroFS appliances on our servers, so that potential
customers can try AeroFS without having to download and deploy a VM image, or fiddle witht their
DNS, email, and other settings. The naive approach would be to automatically provision a new EC2
instance for each customer and load it with an AeroFS appliance. That would work, but it would be
very expensive, as a medium-sized EC2 instance tends to cost around $50/month.

In order to keep costs down, we need to be able to run multiple AeroFS appliances on the same box.
This document explains the challenges that we faced and the solutions that we adopted in order to
achieve this goal.

## Multitenancy

It may be tempting to further reduce server costs with multitenancy, ie. using a single AeroFS
appliance to serve multiple customers. However, that would require very significant changes to our
code base, and would dramatically increase the risk of data leaks (where software bugs allow one
customer to read the data from another customer). For these reasons, we will discard a multitenancy
approach in favor of virtual hosting (ie: running multiple appliances on the same box, each serving
a specific customer).

An exception to that is the relay server (Zephyr). It already has built-in multitenancy support and
it never handles clear-text data. Everything going through Zephyr is encrypted on the client side
and Zephyr merely forwards the data to the right client, eliminating any risk of data leak.
Furthermore, Zephyr consumes significant amounts of bandwidth, so it may be interesting to host it
on a cloud provider with lower bandwidth costs. For these reasons, it makes sense to have a single
instance of Zephyr running on a separete server and serving multiple tenants.

In the rest of this document, we assume that we will have one Zephyr instance serving all Hosted
Private Cloud deployments, and we focus on the other AeroFS services.

## Virtual Hosting

An AeroFS appliance is essentially a bunch of Docker containers talking to each other and exposing
some public services to the world. In order to run multiple instances of these containers on the
same box, we need a mechanism to route the public traffic to the correct instances. This is called
[virtual hosting](https://en.wikipedia.org/wiki/Virtual_hosting).

There are 3 possible ways to do virtual hosting: IP-based, name-based, or port-based:

1. *IP-based:* multiple appliances run on the same box and each appliance gets its own public IP
   address. No reverse-proxying is needed in this case. This is the easiest solution technically
   speaking, but it's impractical due to IPv4 address exhaustion.

2. *Port-based:* only one public IP address for the box, but each appliance exposes its services on
   a different set of ports. Again, no reverse-proxying is needed, but this time the clients needs
   to discover what ports they need to connect to. OK for internal services, but not a good
   solution for user-facing web services (when the user is supposed to connect to the service by
   entering a URL in their browsers).

3. *Name-based:* a reverse-proxy accepts the connection, the client tells the proxy what host it
   intended to connect to, and the proxy forwards the request to the right container. Nicer user
   experience than port-based virtual hosting, but only works for some protocols (namely, HTTP and
   HTTPS with the browser certificate, not the AeroFS certificate). Also, adds complexity and cost
   since things have to go through the reverse-proxy.

As of this writing, an AeroFS appliance exposes the following public services:

    Name       Port   Protocol                      Description

    nginx      80     HTTP                          Web frontend. User facing. Redirects to 443.
    nginx      443    HTTPS, browser certificate    Web frontend. User facing.
    nginx      4433   HTTPS, aerofs certificate     Same as 443 but secured with the AeroFS certificate.
    nginx      5222   HTTPS, aerofs certificate     Same as 4433, but with optional mutual auth.
    havre      8084   Daemon binary protocol        Used by AeroFS clients.
    zephyr     8888   Zephyr protocol               Relay server. Used by AeroFS clients to exchange data.
    lipwig     29438  SSMP                          Used by AeroFS clients.


It follows from the discussion above that we're going to use name-based virtual hosting for
nginx:80 and nginx:443, and port-based virtual hosting for nginx:4433, havre, and lipwig. As for
Zephyr, as discussed above, it will be running on its own server.

# Name-based virtual hosting

We will use nginx as a reverse-proxy to route the traffic to the appropriate containers. The
corollary here is that we will use a single wildcard certificate for each HPC machine, so end-users
will not need to provide us with a certificate and key.

In order for nginx to find which Docker container to route to, we can either:

- Use a config-based approach: listen to Docker events and update the nginx config file to
  add/remove virtual hosts. This can be achieved using
  [nginx-proxy](https://github.com/jwilder/nginx-proxy)
- Use a DNS-based approach: use regular expressions in the nginx config file + an internal DNS
  server ([dnsdock](https://github.com/tonistiigi/dnsdock)) to resolve the IP address of the
  container.

In the DNS-based approach, we use regular expressions in nginx's config to capture the subdomain
that the client is accessing. We then query an internal DNS server to resolve this subdomain to the
appropriate Docker containers. As in the config-based approach, the DNS server uses the Docker
events API to watch for new containers and add them to the DNS table.

Both approaches are roughly equivalent. We choose the DNS approach as it seems cleaner and as being
able to resolve Docker container names to IP addresses might be useful in other cases.

One potential pitfall to watch for in both approaches is the maintenance mode. When the AeroFS
appliance goes to maintenance mode, the main nginx container is stopped and a special
maintenance-nginx container is started. Traffic should now go to the maintenance container. This
can be achieved by:

  - *Config-based approach*: using conditional logic in the configuration template to make a
    special case for maintenance-nginx.
  - *DNS-based approach*: configuring the maintenance container so that its DNS name is the same
    as the regular container. This way, the name "nginx" will resolve to either the maintenance or
    the regular container, depending on which one is up.

## Port-based virtual hosting

For the services that will not use name-based virtual hosting (ie: nginx:4433, havre and lipwig),
we will:

  - Find unused ports on the Docker host
  - Expose the services on those ports
  - Update the configuration with these port numbers so that the clients can find the services.

Docker has an option to automatically expose a service on a random available high port, but we
can't use this as the port will change if the container is destroyed and re-created (in the case of
an update, for example). Therefore, we will need to come up with our mechanism to manage the port
assignement, and tell Docker to use these ports when creating the containers.

We should also probably try to avoid reusing previously-assigned ports after a Hosted Private Cloud
instance has been destroyed so that clients trying to reach the old appliance won't show all sorts
of confusing error messages to the users if they're able to connect to a new instance reusing these
port numbers but with different certificates.

## Container name prefixes

Docker container names must be unique. In order to run multiple instances of each container, we
will prefix each container name with the subdomain associated with the deployement. For example,
assume that Big Corporation, Inc. wants to try Hosted Private Cloud and chooses the subdomain
'bigco'. They will be able to access their Hosted Private Cloud deployment at
`https://bigco.aerofs.com` (or whatever domain we're running the service on). In this example,
all the Docker containers that were created for their Hosted Private Cloud deployment will have the
string "bigco-" prefixed to their name.

## Bootstrapping the containers

In our current architecture, we have a special Docker container called the Loader which is in
charge of pulling, starting, stopping and updating the other containers. The Loader also manages
the container names and appends a version tag to their names so that mutliple versions of the same
container can exist at the same time, allowing for a smooth update process.

We will integrate with the Loader in following way: during the bootstrap process, the Loader will
look at its own name and see if it starts with a prefix. If so, any container created by the Loader
will inherit from this prefix.

## Security aspects

  - Cross-subdomain cookie attacks: we need to investigate if there's any risk of cross-subdomain
    cookie attack.
  - Should we run the service on a separate domain, like "hosted-aerofs.com", or is it ok if we run
    it on our primary domain, possibly on its own subdomain such as "hosted.aerofs.com"? If the
    hosted enviroment is compromposed, our aerofs.com wildcard cert goes out the door as well.
  - There are a few containers that are either privileged or have access to the docker socket (ntp
    and bunker), effectively giving them root access to the host. If these are somehow compromised
    for a single appliance all appliances will be compromised.

## Administration

- We will need some kind of dashboard to monitor the Hosted Private Cloud instances and the servers
  that they're running on.
- We will need some mechanism to deactivate the instances after a trial period of x days has
  expired, with an option for sales people to extend the trial
- We will need a mechanism for automatic server provisioning. General idea: define a maximum number
  of Hosted Private Cloud instances per server. When a user wants to create a new instance, find
  the server with the lowest amount of instances. If it has less than the maximum number of
  instances, start the new instance on that server, otherwise, provision a new server and start the
  instance there.

## Other points to be defined later

- Provide a migration path so that users can migrate from Hosted Private Cloud to full Private
  Cloud with all their data.
- Make sure we integrate well with in-place updates.
- Automation for scaling across multiple HPC machines. For now, the customer success team will
  manually add machines to the HPC cluster via a cloud-init configuration mechanism. Auto-scaling
  will be tackled later.
