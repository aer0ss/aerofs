# IMPORTANT - Conditions for running this test:
#   -   The first actor in the config file needs to be a team server
#   -   If the second actor does not have to be the same user as the first,
#       but they must be in the same organization

import os
import json
import requests

from api import aerofs_oauth
from aerofs_sp import sp as sp_service
from core.block_storage.storage_agent import common
from lib.app.cfg import get_cfg
from lib.files import wait_file_with_content
from syncdet import case
from syncdet.case import instance_unique_string
from syncdet.actors import actor_list
from syncdet.case.sync import sync
from aerofs_common import param


FILENAME = "test_" + instance_unique_string()
CONTENT = "Oops I did it again!!"


def storage_agent():
    API_URL = "https://{}/api/v1.2".format(case.local_actor().aero_host)

    token = common.get_oauth_token_for_user(actor_list()[1])
    s = requests.Session()
    s.headers['Authorization'] = 'Bearer ' + token
    s.headers['Endpoint-Consistency'] = 'strict'

    print 'creating file...'
    r = s.post(API_URL+'/files',
            headers={'Content-Type': 'application/json'},
            data=json.dumps({'parent': "root", 'name': FILENAME}))
    r.raise_for_status()
    etag = r.headers['etag']
    file_id = r.json()['id']
    print 'etag:', etag, 'id:', file_id

    s.headers['Content-Type'] = 'application/octet-stream'
    r = s.put(API_URL+'/files/'+file_id+'/content',
        headers={'If-None-Match': etag, "Route": get_cfg().did().get_hex()},
        data=(CONTENT).encode('utf-8'))
    r.raise_for_status()
    upload_id = r.headers['upload-id']
    print 'upload id is', upload_id
    sync("oid")


def put():
    sync("oid")
    path = os.path.join(get_cfg().get_root_anchor(), FILENAME)
    wait_file_with_content(path, CONTENT)
    print "done"


spec = { 'entries': [storage_agent, put] }
