import connection
import tap_pb2

from transport_pb2 import PBAbortStream

_TRANSPORT_NAME_TO_TYPE = {
    'zephyr': tap_pb2.StartTransportCall.ZEPHYR,
    'jingle': tap_pb2.StartTransportCall.JINGLE,
    'tcpmt': tap_pb2.StartTransportCall.TCPMT
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

    def sendPacket(self, id, sid, payload, highPrio=False):
        '''
        Sends a Unicast or Maxcast packet, depending if 'id' is a DID
        or a Maxcast ID. 'payload' is an array of bytes
        '''
        try:
            # Try getting the bytes of this id. If this succeeds, looks like it's a DID,
            # so send the data as a Unicast packet
            self._service.sendUnicastPacket(id.bytes, sid.bytes, payload, False, highPrio)
        except AttributeError:
            # The id.bytes attribute doesn't exist, so try using id as an int for
            # sending a Maxcast packet
            self._service.sendMaxcastPacket(id, sid.bytes, payload, highPrio)

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

    def awaitEvent(self):
        '''
        Blocks until the Transport layer fires a TransportEvent, and returns
        that event
        '''
        return self._service.awaitTransportEvent()


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
        Aborts the stream with the given PBAbortStream.Reason 'reason'
        '''
        pbReason = PBAbortStream()
        pbReason.reason = reason
        self._service.abortOutgoing(self._streamId, self.did.bytes, pbReason)

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
        reply = self._service.receive()
        return [(chunk.seq_num, chunk.wire_length, chunk.payload) for chunk in reply.chunks]

    def end(self):
        '''
        Ends the stream
        '''
        self._service.endIncoming(self._streamId, self.did.bytes)

    def abort(self, reason):
        '''
        Aborts the stream with the given PBAbortStream.Reason 'reason'
        '''
        pbReason = PBAbortStream()
        pbReason.reason = reason
        self._service.abortIncoming(self._streamId, self.did.bytes, pbReason)