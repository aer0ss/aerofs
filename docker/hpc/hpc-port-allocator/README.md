Hosted Private Cloud Port Allocator Service
===========================================

Rationale
---------

Some AeroFS containers need a publicly-accessible port number on the host machine on which to
publish their service. We use fixed port numbers on Private Cloud (e.g. 29438 for Lipwig, but this
is impossible on Hosted Private Cloud since we have multiple copies of the same service running on
the host.

Therefore, we need a very simple service that manages port allocation on the host. Its job is to say
that lipwig for customer 'foobar' will run on port xxx, and so on.

This service will be queried by the Loader so that the containers can be created on the right ports,
and by the config service to reflect these port allocations in the config.


Design & API
------------

    GET /ports/<subdomain>/<service>

(example: `GET /ports/foo-corp/havre`)

Returns a JSON document with the port number and nothing else. Picks the next available port if it's
the first time we're querying this specific combination of subdomain and service, otherwise returns
the previously allocated port number.


Testing locally
---------------

To run the code locally you can do:

    cd root virtualenv env env/bin/pip install -r requirements.txt sudo env/bin/python main.py

(sudo because we're running on port 80 - change to a port > 1024 if you'd like to avoid sudo)


Port exhaustion
---------------

To keep things simple, we don't try to reuse port numbers after an appliance has been destroyed. We
start at some base port number (like 2000) and keep incrementing. Assuming that each appliance uses
5 ports and there are 63,000 ports available, that means we will exhaust all ports after 12,600
appliances have been created on that server. At that point, it's probably a good idea to throw away
the server and start over, rather than trying to reclaim port numbers.


Future improvements
-------------------

- add APIs to retrieve all ports used by a specific subdomain? `GET /ports/<subdomain>` would return
  a dictionnary of service:port

- add API to delete a subdomain and reclaim the port numbers? Probably not worth it.