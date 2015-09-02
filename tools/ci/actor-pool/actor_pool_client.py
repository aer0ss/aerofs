#!/usr/bin/env python
import requests
import json

url = 'http://127.0.0.1:8040'
headers = {'Content-type': 'application/json', 'Accept': 'application/json'}

if __name__ == "__main__":
    # POST test
    print 'POSTing...'
    a1 = {'addr': '192.168.2.1',
          'vm': True,
          'isolated': False,
          'os': 'w'}
    a2 = {'addr': '192.168.2.2',
          'vm': True,
          'isolated': False,
          'os': 'l'}
    a3 = {'addr': '192.168.2.3',
          'vm': False,
          'isolated': True,
          'os': 'w'}
    actors = [a1, a2, a3]
    r = requests.post(url,
                      data=json.dumps(actors),
                      headers=headers)
    print r.status_code
    print r.json()

    # Acquire test
    print 'GETting...'
    a1 = {'os': 'l'} # any linux actor
    a2 = {'isolated': True} # any isolated actor
    a3 = {'os': 'w', 'isolated': True, 'vm': False} # physical, isolated, windows
    actors = [a1, a3]
    r = requests.post('{}/acquire'.format(url),
                     data=json.dumps(actors),
                     headers=headers)
    print r.status_code
    addresses = r.json()
    print addresses

    # Release test
    addresses = [u'192.168.2.3', u'192.168.2.1']
    print 'returning the actors...'
    r = requests.post('{}/release'.format(url),
                      data=json.dumps(addresses),
                      headers=headers)
    print r.status_code
    print r.json()
