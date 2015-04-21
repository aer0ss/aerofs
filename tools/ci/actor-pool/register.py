#!/usr/bin/env python

import argparse
import re
import sys
import json
import requests

POOL_URL = "http://newci.arrowfs.org:8040"
JSON_HEADERS = {'Content-type': 'application/json', 'Accept': 'application/json'}

OS_CHARS = 'wWlLoO'
TRUE_CHARS = 'yYtT'
FALSE_CHARS = 'nNfF'


if __name__ == "__main__":
    addresses = []

    parser = argparse.ArgumentParser()
    parser.add_argument('infile', nargs='?', type=argparse.FileType('r'), default=sys.stdin)
    parser.add_argument('--os', '-o')
    parser.add_argument('--isolated', '-i')
    parser.add_argument('--vm', '-v')
    args = parser.parse_args()

    # This is unfortunately necessary afaict. Python does not seem to like raw_input after EOF
    # has been detected on stdin
    if not sys.stdin.isatty() and any(a is None for a in [args.os, args.isolated, args.vm]):
        sys.stderr.write('If you\'re going to pipe in addresses, use the --os, --isolated,\n')
        sys.stderr.write('and --vm flags for a truly hands-off experience.\n')
        sys.exit(1)

    if sys.stdin.isatty():
        print 'Enter space-separated addresses:'

    for line in args.infile:
        addresses.extend([w.strip() for w in line.split(' ') if w.strip()])

    print '\nThe following IP\'s will be registered:'
    for addr in addresses:
        print addr
    print

    os = args.os or raw_input("What OS? ")
    if os[0] not in OS_CHARS:
        sys.stderr.write('OS must be windows, osx or linux\n')
        sys.exit(1)
    os = os[0].lower()

    isolated = args.isolated or raw_input("Isolated network? ")
    if isolated[0] not in TRUE_CHARS + FALSE_CHARS:
        sys.stderr.write('Enter yes/true or no/false\n')
        sys.exit(1)
    isolated = isolated[0] in TRUE_CHARS

    vm = args.vm or raw_input("Virtual Machine? ")
    if vm[0] not in TRUE_CHARS + FALSE_CHARS:
        sys.stderr.write('Enter yes/true or no/false\n')
        sys.exit(1)
    vm = vm[0] in TRUE_CHARS

    payload = [dict(addr=a, os=os, isolated=isolated, vm=vm) for a in addresses]
    print payload
    r = requests.post(POOL_URL, data=json.dumps(payload), headers=JSON_HEADERS)
    assert r.status_code == 200
