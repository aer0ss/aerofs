import connection
import tap_pb2
import uuid

from transport_pb2 import PBStream
from tap_pb2 import TransportEvent

# Maps string names to enums used to designate which transport
# to use TAP with
_TRANSPORT_NAME_TO_TYPE = {
    'zephyr': tap_pb2.StartTransportCall.ZEPHYR,
    'jingle': tap_pb2.StartTransportCall.JINGLE,
    'tcpmt': tap_pb2.StartTransportCall.TCPMT
}

# Converts an event to an IncomingStream object
def _eventToIncomingStream(service, event):
    return IncomingStream(service, event.stream_id, \
            uuid.UUID(bytes=event.did), \
            uuid.UUID(bytes=event.sid), \
            event.client, event.high_priority)

# Maps event types to functions that construct and return
# a more pythony representation of the event type
_EVENT_TRANSFORM_MAP = {
    TransportEvent.INCOMING_STREAM_BEGUN: _eventToIncomingStream
}

def connect(rpc_host, transport):
    '''
    Connects to the remote host and returns a Transport object through
    which remote calls can be made. The 'transport' argument specifies
    what transport to use (zephyr, jingle, tcpmt)
    '''
    t = tap_pb2.TapServiceRpcStub(connection.BlockingConnectionService(rpc_host))
    try:
        t.startTransport(_TRANSPORT_NAME_TO_TYPE[transport])
        return Transport(t)
    except KeyError:
        raise Exception('No such transport exists')

class Transport(object):
    '''
    Class that wraps the Protobuf generated RPC stubs. Use this for 
    making TAP calls. Create this class via 'connect'
    '''
    
    def __init__(self, rpcService):
        self._service = rpcService
        self._nextOutgoingId = 1

    def beginStream(self, did, sid, highPrio=False):
        '''
        Begins a new outgoing stream given a DID and SID and returns an OutgoingStream object. Use
        that object to make stream related calls
        '''

        self._service.begin(self._nextOutgoingId, did.bytes, sid.bytes, False, highPrio)
        stream = OutgoingStream(self._service, self._nextOutgoingId, did, sid, False, highPrio)
        self._nextOutgoingId += 1
        return stream

    def sendDatagram(self, id, sid, payload, highPrio=False):
        '''
        Sends a Unicast or Maxcast packet, depending if 'id' is a DID
        or a Maxcast ID. 'payload' is an array of bytes
        '''
        try:
            # Try getting the bytes of this id. If this succeeds, looks like it's a DID,
            # so send the data as a Unicast packet
            self._service.sendUnicastDatagram(id.bytes, sid.bytes, payload, False, highPrio)
        except AttributeError:
            # The id.bytes attribute doesn't exist, so try using id as an int for
            # sending a Maxcast packet
            self._service.sendMaxcastDatagram(id, sid.bytes, payload, highPrio)

    def updateLocalStoreInterest(self, stores_added, stores_removed):
        '''
        Updates the local stores this client is interested in. This method
        takes a list of added SIDs and a list of removed SIDs
        '''

        addedCollection = tap_pb2.UUIDCollectionMessage()
        for store in stores_added:
            addedCollection.uuids.append(store.bytes)

        removedCollection = tap_pb2.UUIDCollectionMessage()
        for store in stores_removed:
            removedCollection.uuids.append(store.bytes)

        self._service.updateLocalStoreInterest(addedCollection, removedCollection)

    def getMaxcastUnreachableOnlineDevices(self):
        '''
        Returns a list of DIDs that are unreachable via Maxcast
        '''
        reply = self._service.getMaxcastUnreachableOnlineDevices()
        return [did for did in reply.uuids]

    def startPulse(self, did, highPrio=False):
        '''
        Sends a pulse request to the given DID. This will result in a TransportEvent
        at some point in the future with success or failure
        '''
        self._service.startPulse(did.bytes, highPrio)

    def awaitRawEvent(self, type=None):
        '''
        Blocks until the Transport layer fires a TransportEvent, and returns
        that event. If type is set to a TransportEvent type, then an event
        of that type will be returned. If a list of types is given, then
        any event matching one of those types will be returned
        '''
        while True:
            event = self._service.awaitTransportEvent()

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

    def awaitEvent(self, type=None):
        '''
        Same as awaitRawEvent, but converts the event received into a python
        object that is easier to work with.
        For example, an event of type INCOMING_STREAM_BEGUN will be converted
        into an IncomingStream object on which you can call receive()
        '''
        event = self.awaitRawEvent(type)
        try:
            transformFunc = _EVENT_TRANSFORM_MAP[event.type]
            return transformFunc(self._service, event)
        except KeyError:
            # If no transform function exists, return the raw event
            return event

# Immutable base class that stores all information related to streams
# Subclasses will only be able to perform actions and not modify data
class _Stream(object):
    
    # Use the object's __setattr__ because our's are not allowed
    def __init__(self, service, streamId, did, sid, client, highPrio):
        object.__setattr__(self, "_service", service)
        object.__setattr__(self, "_streamId", streamId)
        object.__setattr__(self, "did", did)
        object.__setattr__(self, "sid", sid)
        object.__setattr__(self, "client", client)
        object.__setattr__(self, "highPrio", highPrio)
        
    # Disallow setting attributes since this is an immutable class
    def __setattr__(self, *args):
        raise TypeError('Object is immutable')

    # Disallow setting attributes since this is an immutable class
    def __delattr__(self, *args):
        raise TypeError('Object is immutable')

# Immutable class representing an outgoing stream
class OutgoingStream(_Stream):
    '''
    Represents a writable, ordered and reliable stream to another peer
    '''

    def send(self, bytes):
        '''
        Sends a byte array
        '''
        self._service.send(self._streamId, self.did.bytes, bytes)

    def end(self):
        '''
        Ends the stream
        '''
        self._service.endOutgoing(self._streamId, self.did.bytes)

    def abort(self, reason):
        '''
        Aborts the stream with the given PBStream.InvalidationReason 'reason'
        '''
        self._service.abortOutgoing(self._streamId, self.did.bytes, reason)

# Immutable class representing an incoming stream
class IncomingStream(_Stream):
    '''
    Represents a readable, ordered and reliable stream to another peer
    '''

    def receive(self):
        '''
        Blocks until there is data to be read from the stream and
        returns it as a list of tuples, each tuple representing a chunk
        of data in the form (sequence_number, wire_length, data)
        '''
        reply = self._service.receive(self._streamId, self.did.bytes)
        return [(chunk.seq_num, chunk.wire_length, chunk.payload) for chunk in reply.chunks]

    def end(self):
        '''
        Ends the stream
        '''
        self._service.endIncoming(self._streamId, self.did.bytes)

    def abort(self, reason):
        '''
        Aborts the stream with the given PBStream.InvalidationReason 'reason'
        '''
        self._service.abortIncoming(self._streamId, self.did.bytes, reason)