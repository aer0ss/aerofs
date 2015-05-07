#!/usr/bin/env python

import json
import requests

POOL_URL = "http://ci.arrowfs.org:8040"
JSON_HEADERS = {'Content-type': 'application/json', 'Accept': 'application/json'}


if __name__ == "__main__":

    windows8 = dict(addr='192.168.128.254', os='w', isolated=True, vm=False)
    macbook = dict(addr='192.168.128.243', os='o', isolated=True, vm=False)

    payload = [windows8, macbook]
    r = requests.post(POOL_URL, data=json.dumps(payload), headers=JSON_HEADERS)
    assert r.status_code == 200
