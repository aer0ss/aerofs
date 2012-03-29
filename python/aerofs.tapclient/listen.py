import sys

from tap import transport

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print "Port required"
        sys.exit(-1)

    service = transport.connect(('localhost', int(sys.argv[1])), 'zephyr')

    while True:
        event = service.awaitEvent()
        print event
