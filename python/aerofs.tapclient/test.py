import uuid
import time

from tap import transport
from tap.transport import TransportEvent
from tap.transport import PBStream

if __name__ == '__main__':
    didA = uuid.UUID("c83b0d574e354139988f4c8e84c7a11c")
    didB = uuid.UUID("18d601269b8e4f26baaf9443b72eeddc")
    sid = uuid.uuid5(uuid.NAMESPACE_DNS, "g.arrowfs.org")

    peerA = transport.connect(('localhost', int(3001)), 'zephyr')
    peerB = transport.connect(('localhost', int(3002)), 'zephyr')

    time.sleep(5)

    # Ensure both peers are on the same store
    peerA.updateLocalStoreInterest([sid], [])
    peerB.updateLocalStoreInterest([sid], [])

    peerA.sendDatagram(didB, sid, 'Yo man!')

    datagram = peerB.awaitEvent(TransportEvent.DATAGRAM_RECEIVED)
    print "From Peer A: %s" % datagram.payload

    peerB.sendDatagram(didA, sid, 'Hey buddy, send me some stuff')

    datagram = peerA.awaitEvent(TransportEvent.DATAGRAM_RECEIVED)
    print "From Peer B: %s" % datagram.payload

    out = peerA.beginStream(didB, sid)
    out.send('Wazzzupp')

    ins = peerB.awaitEvent(TransportEvent.INCOMING_STREAM_BEGUN)
    print "From PeerA: %s" % ins.receive()[0][2]

    out.abort(PBStream.OUT_OF_ORDER)

    try:
        ins.receive()
    except Exception as e:
        print e

    peerB.sendDatagram(didA, sid, 'common, man!')

    datagram = peerA.awaitEvent(TransportEvent.DATAGRAM_RECEIVED)
    print "From Peer B: %s" % datagram.payload