import uuid
import time
import sys

from tap import transport
from tap import tap_pb2

if __name__ == '__main__':
    didB = uuid.UUID("18d601269b8e4f26baaf9443b72eeddc")
    sid = uuid.uuid5(uuid.NAMESPACE_DNS, "g.arrowfs.org")

    peerA = transport.connect(('localhost', int(3001)), 'zephyr')
    peerB = transport.connect(('localhost', int(3002)), 'zephyr')

    time.sleep(5)

    peerA.updateLocalStoreInterest([sid], [])
    peerB.updateLocalStoreInterest([sid], [])

    peerA.sendPacket(didB, sid, 'Is there anyone home?')

    while True:
        event = peerB.awaitEvent()
        if event.type == tap_pb2.TransportEvent.UNICAST_PACKET_RECEIVED:
            print "Unicast packet received {"
            print "\tfrom DID:     %s" % uuid.UUID(bytes=event.did)
            print "\ton store SID: %s" % uuid.UUID(bytes=event.sid)
            print "\tis client:    %s" % event.client
            print "\tpayload:      %s" % event.payload
            print "}"
            break
