#!/usr/bin/python

import binascii
import sys

if len(sys.argv) < 2:
    print 'Usage: {0} <string1> [<string2> <string3>  ...]'.format(sys.argv[0])
    sys.exit()

crcs = [(binascii.crc32(s) & 0xffffffff) for s in sys.argv[1:]]

print ' | '.join(['%08x' % crc for crc in crcs])
