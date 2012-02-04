import uuid

from tap import transport

if __name__ == '__main__':
	service = transport.connect(('localhost', 3001), 'zephyr')

	did = uuid.uuid4()
	sid = uuid.uuid4()
	
	stream = service.beginStream(did, sid)
	stream.send('Hello? Is there anybody in there?')
	stream.end()

	stream = service.beginStream(did, sid)
	stream.send('Just nod if you can hear me')
	stream.abort(transport.PBAbortStream.OUT_OF_ORDER)

	service.sendPacket(1, sid, 'Is there anyone home?')

	try:
		service.awaitEvent()
	except Exception as e:
		print e



