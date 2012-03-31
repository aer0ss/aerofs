import uuid
import time
import threading

from tap import transport
from tap.transport import TransportEvent
from tap.transport import PBStream

sid = uuid.uuid5(uuid.NAMESPACE_DNS, "g.arrowfs.org")

def peerA():
    didB = uuid.UUID("18d601269b8e4f26baaf9443b72eeddc")
    tp = transport.connect(('localhost', int(3001)), 'zephyr')
    time.sleep(5)
    tp.updateLocalStoreInterest([sid], [])

    for x in range(100):
        print "Sending %d" % x
        tp.sendDatagram(didB, sid, 'Message %d' % x)

def peerB():
    didA = uuid.UUID("c83b0d574e354139988f4c8e84c7a11c")
    tp = transport.connect(('localhost', int(3002)), 'zephyr')
    time.sleep(5)
    tp.updateLocalStoreInterest([sid], [])

    for x in range(100):
        d = tp.awaitEvent(TransportEvent.DATAGRAM_RECEIVED)
        print "iter %d: %s" % (x, d.payload)

if __name__ == '__main__':

    t1 = threading.Thread(target=peerA)
    t2 = threading.Thread(target=peerB)

    t1.start()
    t2.start()

    t1.join()
    t2.join()