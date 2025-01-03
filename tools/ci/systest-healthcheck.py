#!/usr/bin/env python
"""
Basic healthchecks prior to running system tests
"""

import os
import requests
import sys

SP_URL = "https://" + os.getenv('APPLIANCE_HOST', 'ci.syncfs.com') + ":4433/sp"
POOL_URL = "http://ci.arrowfs.org:8040"
JSON_HEADERS = {'Content-type': 'application/json', 'Accept': 'application/json'}


if __name__ == "__main__":
    print "check appliance..."
    requests.get(SP_URL, verify=False, timeout=10)

    print "check actor pool..."
    r = requests.get('{}'.format(POOL_URL), headers=JSON_HEADERS)
    assert r.ok
    addresses = r.json().get('actors')
    assert len(addresses) > 0

    print "check actors..."
    for addr in addresses:
        print " > check actor @ {}".format(addr)
        os.system("ssh-keygen -f ~/.ssh/known_hosts -R {}".format(addr))
        assert os.system("ssh -o ConnectTimeout=5 -o StrictHostKeyChecking=no aerofstest@{} exit".format(addr)) == 0

