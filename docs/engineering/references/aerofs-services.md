The AeroFS system consists of a large number of smaller services, in addition to the clients and Team Servers.

# Service Provider, SP, `sp.aerofs.com`

---
# Devmail, `devmail.aerofs.com`

Devmail runs postfix, and sends local prod developer email as well as email for lizard when running in development mode.

---
# Verkehr, `verkehr.aerofs.com`

The push notification service for events sent from central services to client applications.

---
# Command server, `c.aerofs.com`

Used to queue command notifications from central services to client applications. The actual push of the command is done by Verker. Think it as an Amazon SQS for AeroFS specific commands.

---
# XMPP, `x.aerofs.com`

## Overview

We run an instance of [ejabberd](http://www.ejabberd.im/) on `x.aerofs.com` for clients to use for discovery.  Due to the higher IO characteristics, we currently run this server on Digital Ocean.

## Administration

Logs are in `/var/log/ejabberd/`.  ejabberd should be listening on port 5222, the jabber port.  We redirect port 443 to this with an iptables rule.

### Statistics

As root:

    ejabberdctl connected_users_number

### Restarting the service

Ideally:

    sudo /etc/init.d/ejabberd stop
    sudo /etc/init.d/ejabberd start

In practice, if something goes awry, you may need to kill `epmd` (the erlang port-mapper daemon) if it starts as the wrong user.  Both `epmd` and `ejabberd` (`beam` or `beam.smp`, depending on whether we enabled SMP or not) should run as the `ejabberd` user.

## Inner workings

### Authentication

The XMPP server runs with a certificate signed by the 

ejabberd requires that you authenticate users in some manner, but we don't care about authenticating users on jabber, so we have a script `/etc/ejabberd/auth_all` (source: `puppetmaster/modules/ejabberd/files/auth_all`) which 

### Channels

The client daemon will connect to the XMPP server and join a chat room for each store they are a member of.  Maxcast messages for a store (which are unauthenticated) are sent to this chat room.

If a client wants to establish a connection to another client, they will exchange candidate information over (not sure, ask Allen or read the source).

---
# Zephyr, `zephyr.aerofs.com`

## Overview

Zephyr is a stupid-simple relay server.  All signaling is performed out-of-band; it is strictly a data transport protocol.  

Zephyr is a high-bandwidth service.  We use it as a fallback to ensure that even NATed and firewalled clients are able to sync.  When other transports have issues, zephyr utilization tends to go up dramatically.

### Implementations

There exist two full zephyr server implementations, and a small swarm of client implementations.

The primary server implementation is written in Java with NIO (source: `src/zephyr`), but we are currently testing the go-lang implementation ("[valkyrie](https://github.arrowfs.org/alleng/valkyrie)") in production, since the Java one has been having some issues.

The AeroFS desktop client uses a Java client implementation.  There is also a Go client implementation in the [valkyrie project](https://github.arrowfs.org/alleng/valkyrie), and a Python implementation Drew uses for sanity checking.

### Deployment

`zephyr.aerofs.com` is currently hosted on Digital Ocean because transit is cheaper there than on AWS.  It sits on the VPN so that it can reach and be reached by puppet.

### Zephyr Protocol

In ~standard protocol description language, where | means concatenation:

* client connects to zephyr server over TCP.
* zephyr server sends [4 octets `ZEPHYR_MAGIC` (big-endian 0x829644a1) | 4 octets zid length (0x00000004) | 4 octets `zid` (random identifier)]
* client exchanges zids with the peer it wants to connect to, receiving `remote_zid`
* client sends a binding request: [`ZEPHYR_MAGIC` | 4 octets peer endpoint length (0x00000004) | 4 octets remote zid (obtained from peer out-of-band, and peer obtained it from the zephyr server)]
* If two clients send a binding request with remote_zid set to the other's zid, then any future bytes sent on that TCP socket are forwarded on to the other peer through their socket.
* This persists until one closes the socket, at which point the partner socket is also closed.

---
# Rocklog, `rocklog.aerofs.com`

See [Rocklog](rocklog.html).

---
# Puppet, `puppet.arrowfs.org`

---
# Devman, same box as SP

---
# VPN, `vpn.arrowfs.org`

See [VPN](vpn.html).

---
# Certificate Authority, CA, `joan.aerofs.com`

It has a public hostname, but should be made internal-only.

---
# Build box, `b.arrowfs.org`

---
# PagerDuty probe runner, `z.arrowfs.org`

---
# MySQL

We currently use Amazon's RDS service for our relational databases.  The SP and SV databases are all backed by RDS.

---
# Redis

We use redis instances on several machines for storage of a more key-value or list nature.  In particular, syncstat, the persistent queue system, and devman all use redis as their backing store.
