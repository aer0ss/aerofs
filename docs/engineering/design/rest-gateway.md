Havre: a public REST gateway
============================

This document describes the design of the REST gateway and the rationale
behind any non-obvious decision. It is intended for internal consumption
only. Any consideration relevant to end-users of the API should make its
way into the public rest_apidoc.


## Tunneling

Havre incorporates a simple tunneling component to allow communication
with REST-enabled daemons behind firewalls. Although the design of this
component was driven by the needs of the gateway, it is intended to be
clean and re-usable.

### Architecture

The goal of the tunnel is to turn a client/server relationship upside down.
Daemons establish a persistent connection to the publicly accessible gateway
and wait for incoming packets.

The client socket (i.e. the daemon) now acts as a server, generating virtual
channels that can be used in place of (or side by side with) channels generated
by a server socket accepting connections.

The accepted socket on the server now acts as a client factory, generating
virtual channels that can be used in place of (or side by side with)
traditional client socket channels.

The virtual channels are multiplexed on the underlying socket channel.
The only ordering guarantee offered by the tunnel is that the bytes written
to a virtual channel will be read in the same order on the corresponding
virtual channel on the other side of the tunnel.

This allow a neat separation of concerns where the upper layers (REST
service in the daemon and HTTP proxy in the gateway) can remain blissfully
unaware of the gory details of tunneling and even mix and match virtual
channels with traditional socket channels.

### Security considerations

Tunnel connections are SSL/TLS encrypted. Daemon-like certs are used
and CName verification is performed, reusing the handshaking code
from the core transport stack.

### Heartbeat

Firewalls will close persistent connections after a delay of inactivity,
typically around 60s. To prevent disruption, both sides of the tunnel send
a special heartbeat packet every 30s.

The operating system will sometimes fail to report socket disconnections
when the physical link is broken. To prevent wasting resources on zombie
connections, the tunnel will automatically close channels that did not
receive a heartbeat in the last 60s.

The heartbeat logic is done at the level of the tunnel protocol and upper
layers can remain blissfully unaware of it.

### Protocol

Tunnel messages have the following format:

|       Field     |       Size     |
|-----------------|----------------|
| `size`          | 2 bytes        |
| `type`          | 2 bytes        |
| `connection_id` | 4 bytes        |
| `payload`       | `size`-6 bytes |
|-----------------|----------------|

The following message types are defined:

|        Type     |                        Purpose                         |
|-----------------|--------------------------------------------------------|
| `MSG_BEAT`      | Heartbeat, to keep connection open                     |
| `MSG_CLOSE`     | Close virtual channel at other end                     |
| `MSG_CLOSED`    | Acknowledge `MSG_CLOSE`                                |
| `MSG_SUSPEND`   | Mark virtual channel at other end as non-writable      |
| `MSG_RESUME`    | Mark virtual channel at other end as writable          |
| `MSG_PAYLOAD`   | Send a payload to the virtual channel at the other end |
|-----------------|--------------------------------------------------------|

NB: All per-channel signalling messages are required to correctly implement
virtual Netty channels and multiplex them over a single physical connection.
Without these trying to streaming anything more than a few hundred bytes will
result in either hanging or OOMing.

### Connection identifier

With the notable exception of `MSG_BEAT`, all messages MUST include a
`connection_id` field.

Identifiers MUST be unique within a physical tunnel connection as they are
used to mux/demux messages.

Identifiers are assigned on the side of the tunnel openning the virtual
channel (i.e. on the gateway). The current implementation just uses the
Netty Channel id, which is a pseudo-randoly generated integer, guaranteed
to be unique among all open Netty channels.

## HTTP proxy

The second component of the gateway is a fairly simple transparent HTTP proxy
that forwards client requests to an appropriate REST-enabled daemon.

### Vocabulary

A "downstream" connection is a client<->gateway connection.

An "upstream" connection is a gateway<->daemon connection.

### Encryption

All incoming connections are SSL/TLS-encrypted.

In a prod deployment there are two possible scenarios:

 - gateway handling SSL on its own
 - gateway sitting behind nginx which handles SSL for us

It is not possible to offer end-to-end encryption with a pure HTTPS connection.
This is an acceptable limitation for private deployment and even for public
deployment it is an inherent limitation of web-access.

However mobile clients within a public deployment should arguably offer it so
we should revisit this in a future iteration. Note that public deployments
encompass not only our "original" public deployment but also potential reselling/
partnership schemes where our customer control servers for their own customers.

### Consistency model

The gateway offers a best-effort session consistency. Within a session it will
try to talk to the same daemon for all requests but will pick any suitable
daemon if the last one is no longer available.

HTTP cookies are used to store session information. The following cookies are used:

|    Name   |                        Content                         |
|-----------|--------------------------------------------------------|
| `server`  | DID from which the last response came                  |
|-----------|--------------------------------------------------------|

This cookie will be updated on every response, which allows the client to check for
consistency breaks. However it is sometimes desirable not to make the request at all
if the previous daemon cannot be reached.

In such cases, the client can use the `Endpoint-Consistency: strict` request header
to cause the request to fail with a `503 Service Unavailable` instead of falling
back to a different daemon.

### Client authentication

The gateway rejects any request missing a valid OAuth access token.

Token validation is required when opening the upstream connection as it is the only
way to pick a suitable daemon (userid and orgid are derived from the token).

Another benefit of validating tokens is that it prevents invalid requests from being
forwarded to daemons which would waste bandwidth and CPU.

The gateway only validates the token for the first request and expects the daemon
to validate token(s) associated with each subsequent requests. This implies that it
is not possible to use tokens associated with different users within a persistent
HTTP connection.

Aside: HTML5 streaming is amazing, however there is now way to specify extra
headers to be sent with the GET requests used under the hood. This means no
streaming unless we pass the OAuth token as a query parameter. Note that other
similar web-access scenarios suffer from the same issue, but media streaming
is an interesting example as it really exercises the performance and reliability
of the gateway and daemon as well as the handling of partial requests.

### Daemon selection

The gateway uses the user information associated with the OAuth access token
to find a daemon suitable to service the requests.

In the first iteration the selection is random among the pool of available
devices associated with the user (a device is associated with a user if it
belongs to that user or is a Team Server for the organization that user
belongs to). In the future it would be interesting to estimate latency and
bandwidth and try to distribute the load optimally.

### Transparency

The gateway is a mostly transparent proxy: only modifies a select few HTTP
headers as to implement the consistency model described above.

NB: this means that there may be slight changes in headers (reordering,
whitespace normalization, ...)

### Conflict handling

If multiple tunnels are open for the same (UserID, DID) the last one wins.
