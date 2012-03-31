import uuid
import time
import sys
import code

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

    print "peerA(didA) is connected to TapServer:zephyr on port 3001"
    print "peerB(didB) is connected to TapServer:zephyr on port 3002"
    print
    code.interact(local=locals())