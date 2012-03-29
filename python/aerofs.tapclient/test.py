import uuid
import time
import sys

from tap import transport

if __name__ == '__main__':
	didB = "18d601269b8e4f26baaf9443b72eeddc".decode("hex")
	sid = uuid.uuid5(uuid.NAMESPACE_DNS, "g.arrowfs.org")

	peerA = transport.connect(('localhost', int(3001)), 'zephyr')
	peerB = transport.connect(('localhost', int(3002)), 'zephyr')

	peerA.updateLocalStoreInterest([sid], [])
	peerB.updateLocalStoreInterest([sid], [])

	peerA.sendPacket(didB, sid, 'Is there anyone home?')

	print peerB.awaitEvent()