Lipwig
======

A new Postmaster General to replace ejabberd


Protocol
--------

Lipwig is the reference implementation of SSMP: the Stupid-Simple Messaging Protocol.

SSMP is a text-based protocol that mix requests/responses in the style of HTTP, NMTP,
and SMTP and push notifications similar to HTML5 Server Sent Events.

Key design goals:
  - Text-based, for easy debugging
  - Interleave request/responses and server events on a single connection
  - Simple enough that a client or server can be written in pretty much any language
    in a matter of hours


## Message format

Each message is a LF-delimited sequence of UTF-8 encoded unicode codepoints.

Messages MUST NOT exceed 1024 bytes. The LF delimiter counts toward the message
length limit.

Servers and clients MUST NOT use more than a single delimiter between messages.

A message is composed of space-separated fields of the following types:

  - `VERB`       `[A-Z]+`
  - `IDENTIFIER` `[a-zA-Z0-9.:@/_-+=~]+`
  - `PAYLOAD`    `[^\n]+`
  - `CODE`       `[0-9]{3}`


Client request: `<VERB> <IDENTIFIER>* <PAYLOAD>?`

Server response: `<CODE> <PAYLOAD>?`

Server event: `000 <IDENTIFIER> <VERB> <IDENTIFIER>? <PAYLOAD>?`


## Response codes

`200` OK
`400` Bad Request
`401` Unauthorized
`404` Not Found
`405` Not Allowed

The special `000` code is used to distinguish server events from request/responses,
thereby allowing events to be freely interleaved with regular responses on the same
connection.

## Login

The first request in any connection MUST be a `LOGIN`.

The server MUST send an appropriate error response and close the connection upon
receiving an invalid `LOGIN` request.

If no `LOGIN` request is received after a reasonable period of time, typically a
few seconds, the server MUST close the connection, without sending any response.

`LOGIN <id> <scheme> <credential>`

The `id` field is of type `IDENTIFIER`.

The `scheme` field is of type `IDENTIFIER`.

The `credential` field is of type `PAYLOAD`, allowing arbitrary authentication
methods to be offered by the server.

All servers that accept connection over SSL/TLS MUST allow authentication through
client certificates. The `scheme` value for cert-based authentication is `cert`.
When a connection is made with a client certificate, LOGIN should succeed for any
`<id>` matching either the Common Name or one of the Subject Alternative Names
specified in the certificate.


## Anonymous connections

The server MAY allow login with the reserved `.` user identifier.

Anonymous connections are not able to subscribe to any topic and cannot be the
destination of unicast messages but they can publish messages to existing
topics.

Anonymous connections SHOULD use some form of authentication to avoid spamming.

## Ping

To test connection liveness and prevent closure by aggressive firewalls.

Clients SHOULD send a ping after a configurable period where no server event
is received, typically about 30s.

Servers SHOULD send a ping after a configurable period where no client request
is received, typically about 30s.

`PING` and `PONG` are unique among client messages in that do not receive a
response message.

`PING` and `PONG` events use the anonymous user identifier as their provenance.

client ping: `PING` -> server ack: `000 . PONG`

server ping: `000 . PING` -> client ack: `PONG`


## Topics

### Subscribe to multicast topic

`SUBSCRIBE <topic> [PRESENCE]`

The optional `PRESENCE` flag can be used to subscribe to presence notifications,
i.e. to get `SUBSCRIBE` and `UNSUBSCRIBE` events when other peers subscribe to or
unsubscribe from the topic.

When the `PRESENCE` flag is provided, the caller will receive an initial batch of
`SUBSCRIBE` events for all existing subscribers and subsequently, `SUBSCRIBE`
and `UNSUBSCRIBE` events as topic membership changes.

The server must ensure that presence notifications are delivered in a safe order.
Crucially, if peer A subscribe to a topic T and peer B unsubscribes from it, the
server must ensure that peer A either does not receive a `SUBSCRIBE` event about B
or receives it before the `UNSUBSCRIBE` event.

Any `SUBSCRIBE` request from an anonymous user should be rejected with code `405`.

### Unsubscribe from multicast topic

`UNSUBSCRIBE <topic>`

Any `UNSUBSCRIBE` request from an anonymous user should be rejected with code `405`.

## Messages

Message delivery:
  - in-order: two messages from the same sender to the same recipient MUST NOT arrive
    out of order at the recipient.
  - no recipient ack: `200` response indicates message sent, not necessarily received
  - at most once: recipients MUST NOT receive duplicate messages

### Unicast

Send message to a single peer.

`UCAST <user> <payload>`

### Multicast

Send message to all peers subscribed to a given topic.

`MCAST <topic> <payload>`

NB: One does not need to be subscribed to a topic to send messages to it.

### Broadcast

Broadcast to all peers sharing at least one topic.

`BCAST <payload>`

Any `BCAST` request from an anonymous user should be rejected with code `405`.

NB: Peers that share multiple topics with the sender MUST NOT receive
multiple identical `BCAST` events.

## Events


### Presence

`000 <from> SUBSCRIBE   <topic>`
`000 <from> UNSUBSCRIBE <topic>`

### Unicast

`000 <from> UCAST <payload>`

### Multicast

`000 <from> MCAST <topic> <payload>`

### Broadcast

`000 <from> BCAST <payload>`

