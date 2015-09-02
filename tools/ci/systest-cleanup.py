#!/usr/bin/env python
"""
This script reads a syncdet yaml file and attempts to return the actors listed to the ci pool.
"""

import yaml
import requests
import sys
import json

POOL_URL = "http://ci.arrowfs.org:8040"
JSON_HEADERS = {'Content-type': 'application/json', 'Accept': 'application/json'}


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print 'Usage: {} [file.yaml]'.format(sys.argv[0])
        sys.exit(1)

    contents = yaml.load(open(sys.argv[1], 'r'))
    try:
        addresses = [a.get('address') for a in contents['actors']]
    except TypeError:
        print 'It looks like there are no actors to free! Exiting happily.'
        sys.exit(0)

    r = requests.post('{}/release'.format(POOL_URL),
                      data=json.dumps(addresses),
                      headers=JSON_HEADERS)

    assert r.ok
