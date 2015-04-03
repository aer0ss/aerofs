"""
TAP is a testing utility for use with the Transport layer of the
AeroFS Daemon. It communicates with an AeroFS Daemon process
via Protobuf-based RPC calls.

Calling 'connect' returns the object which exposes all of TAP's
methods. Just do:

    from lib import tap

    transport = tap.connect('zephyr', 'somehost', PORT_NUM)
    stream = transport.begin_stream(...)

Any errors that may occur will be raised as exceptions from the
offending method.

All mention of DID and SID refer to a 'uuid' object which can
be generated via the builtin python 'uuid' module
"""

import uuid

from aerofs_ritual import connection
from aerofs_ritual.gen.tap_pb2 import TransportEvent

# Maps string names to enums used to designate which transport
# to use TAP with
from aerofs_ritual.gen import tap_pb2

_TRANSPORT_NAME_TO_TYPE = {
    'zephyr': tap_pb2.StartTransportCall.ZEPHYR,
    'tcpmt': tap_pb2.StartTransportCall.TCPMT
}

# Maps event types to functions that construct and return
# a more pythony representation of the event type
_EVENT_TRANSFORM_MAP = {
    TransportEvent.INCOMING_STREAM_BEGUN: lambda service, event: IncomingStream(service, event.stream_id, \
                                                                        uuid.UUID(bytes=event.did), \
                                                                        uuid.UUID(bytes=event.sid), \
                                                                        event.client, event.high_priority),
    TransportEvent.PRESENCE_CHANGED: lambda service, event: Presence(uuid.UUID(bytes=event.did), \
                                                                     uuid.UUID(bytes=event.sid), \
                                                                     event.online),
}

def connect(transport, rpc_host='localhost', rpc_port=3001):
    """
    Connects to the remote host and returns a Transport object through
    which remote calls can be made. The 'transport' argument specifies
    what transport to use (zephyr, tcpmt)
    """
    t = tap_pb2.TapServiceRpcStub(connection.SyncConnectionService(rpc_host, rpc_port))
    try:
        t.start_transport(_TRANSPORT_NAME_TO_TYPE[transport])
        return Transport(t)
    except KeyError:
        raise Exception('No such transport exists')

class Transport(object):
    """
    Class that wraps the Protobuf generated RPC stubs. Use this for
    making TAP calls. Create this class via 'connect'
    """

    def __init__(self, rpc_service):
        self._service = rpc_service
        self._next_outgoing_id = 1

    def begin_stream(self, did, sid, high_prio=False):
        """
        Begins a new outgoing stream given a DID and SID and returns an OutgoingStream object. Use
        that object to make stream related calls
        """

        self._service.begin(self._next_outgoing_id, did.bytes, sid.bytes, False, high_prio)
        stream = OutgoingStream(self._service, self._next_outgoing_id, did, sid, False, high_prio)
        self._next_outgoing_id += 1
        return stream

    def send_datagram(self, id, sid, payload, high_prio=False):
        """
        Sends a Unicast or Maxcast packet, depending if 'id' is a DID
        or a Maxcast ID. 'payload' is an array of bytes
        """
        try:
            # Try getting the bytes of this id. If this succeeds, looks like it's a DID,
            # so send the data as a Unicast packet
            self._service.send_unicast_datagram(id.bytes, sid.bytes, payload, False, high_prio)
        except AttributeError:
            # The id.bytes attribute doesn't exist, so try using id as an int for
            # sending a Maxcast packet
            self._service.send_maxcast_datagram(id, sid.bytes, payload, high_prio)

    def update_local_store_interest(self, stores_added, stores_removed):
        """
        Updates the local stores this client is interested in. This method
        takes a list of added SIDs and a list of removed SIDs
        """

        added_collection = tap_pb2.UUIDCollection()
        for store in stores_added:
            added_collection.uuids.append(store.bytes)

        removed_collection = tap_pb2.UUIDCollection()
        for store in stores_removed:
            removed_collection.uuids.append(store.bytes)

        self._service.update_local_store_interest(added_collection, removed_collection)

    def get_maxcast_unreachable_online_devices(self):
        """
        Returns a list of DIDs that are unreachable via Maxcast
        """
        reply = self._service.get_maxcast_unreachable_online_devices()
        return [did for did in reply.uuids]

    def pulse(self, did, high_prio=False):
        """
        Sends a pulse request to the given DID. This will result in a TransportEvent
        at some point in the future with success or failure
        """
        self._service.pulse(did.bytes, high_prio)

    def await_raw_event(self, type=None):
        """
        Blocks until the Transport layer fires a TransportEvent, and returns
        that event. If type is set to a TransportEvent type, then an event
        of that type will be returned. If a list of types is given, then
        any event matching one of those types will be returned
        """
        while True:
            event = self._service.await_transport_event()

            if not type:
                # Don't filter the event type, just return the first event
                return event
            else:
                try:
                    # The type may be a list of types, so check if the event
                    # matches one
                    for t in type:
                        if event.type == t:
                            return event
                except TypeError:
                    # The type is a single type, check if it matches the event
                    if event.type == type:
                        return event

    def await_event(self, type=None):
        """
        Same as await_raw_event, but converts the event received into a python
        object that is easier to work with.
        For example, an event of type INCOMING_STREAM_BEGUN will be converted
        into an IncomingStream object on which you can call receive()
        """
        event = self.await_raw_event(type)
        try:
            transform_func = _EVENT_TRANSFORM_MAP[event.type]
            return transform_func(self._service, event)
        except KeyError:
            # If no transform function exists, return the raw event
            return event

    def deny_none(self):
        """
        Clears the list of message types TAP should deny from sending out
        """
        self._service.deny_none()

    def deny_all(self):
        """
        Tells TAP to deny all message types from being sent out
        """
        self._service.deny_all()

    def deny(self, types):
        """
        Tells TAP to deny the given message types. They can be either a single
        PBTPHeader.Type or a list of them
        """
        if types:
            m = tap_pb2.MessageTypeCollection()
            try:
                m.types.extend(types)
            except TypeError:
                # The type is a single type
                m.types.append(types)

            self._service.deny(m)

# Immutable base class that stores all information related to streams
# Subclasses will only be able to perform actions and not modify data
class _Stream(object):

    # Use the object's __setattr__ because our's are not allowed
    def __init__(self, service, stream_id, did, sid, client, high_prio):
        object.__setattr__(self, "_service", service)
        object.__setattr__(self, "_stream_id", stream_id)
        object.__setattr__(self, "did", did)
        object.__setattr__(self, "sid", sid)
        object.__setattr__(self, "client", client)
        object.__setattr__(self, "high_prio", high_prio)

    # Disallow setting attributes since this is an immutable class
    def __setattr__(self, *args):
        raise TypeError('Object is immutable')

    # Disallow setting attributes since this is an immutable class
    def __delattr__(self, *args):
        raise TypeError('Object is immutable')

# Immutable class representing an outgoing stream
class OutgoingStream(_Stream):
    """
    Represents a writable, ordered and reliable stream to another peer
    """

    def send(self, bytes):
        """
        Sends a byte array
        """
        self._service.send(self._stream_id, self.did.bytes, bytes)

    def end(self):
        """
        Ends the stream
        """
        self._service.end_outgoing(self._stream_id, self.did.bytes)

    def abort(self, reason):
        """
        Aborts the stream with the given PBStream.InvalidationReason 'reason'
        """
        self._service.abort_outgoing(self._stream_id, self.did.bytes, reason)

# Immutable class representing an incoming stream
class IncomingStream(_Stream):
    """
    Represents a readable, ordered and reliable stream to another peer
    """

    def receive(self):
        """
        Blocks until there is data to be read from the stream and
        returns it as a list of tuples, each tuple representing a chunk
        of data in the form (sequence_number, wire_length, data)
        """
        reply = self._service.receive(self._stream_id, self.did.bytes)
        return [(chunk.seq_num, chunk.wire_length, chunk.payload) for chunk in reply.chunks]

    def end(self):
        """
        Ends the stream
        """
        self._service.end_incoming(self._stream_id, self.did.bytes)

    def abort(self, reason):
        """
        Aborts the stream with the given PBStream.InvalidationReason 'reason'
        """
        self._service.abort_incoming(self._stream_id, self.did.bytes, reason)

class Presence(object):
    """
    Represents a peer's online Presence
    """

    def __init__(self, did, sid, online):
        self.did = did
        self.sid = sid
        self.online = online
