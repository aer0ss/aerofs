# IMPORTANT - Conditions for running this test:
#   -   The first actor in the config file needs to be a storage agent
#   -   If the second actor does not have to be the same user as the first,
#       but they must be in the same organization

import os
import requests
import time

from aerofs_common import param
from aerofs_sp import sp as sp_service
from core.block_storage.storage_agent import common
from lib.app.cfg import get_cfg
from lib.files import instance_unique_path
from syncdet import case
from syncdet.case import instance_unique_string
from syncdet.case.assertion import assertEqual
from syncdet.case.sync import sync
from syncdet.actors import actor_list


FILENAME = "test_" + instance_unique_string()
CONTENT = "This is sparta"
MAX_ATTEMPTS=10

def storage_agent():
    API_URL = "https://{}/api/v1.2".format(case.local_actor().aero_host)
    sync("file_oid")

    token = common.get_oauth_token_for_user(actor_list()[1])

    attempts = 0
    while attempts < MAX_ATTEMPTS:
        r = requests.get(API_URL + "/folders/root/children",
            headers={"Authorization": "Bearer " + token},
            params={"fields": "path,children"})
        r.raise_for_status()
        if len(r.json()['files']) > 0:
            break;
        time.sleep(param.POLLING_INTERVAL)
        attempts += 1

    match = [f for f in r.json()['files'] if f['name'] == FILENAME]
    file_id = match[0]['id']
    print 'file_name is {}, id {}'.format(match[0]['name'], file_id)

    while True:
        r = requests.get(API_URL + "/files/" + file_id + '/content',
            headers={"Route": get_cfg().did().get_hex()},
            params={"token": token})

        if r.status_code == 200 or r.status_code == 404:
            if r.text != "":
                break
            time.sleep(param.POLLING_INTERVAL)
            continue
        else:
            r.raise_for_status()

    assertEqual(str(r.text), CONTENT)


def put():
    path = os.path.join(get_cfg().get_root_anchor(), FILENAME)
    print 'put', path
    with open(path, "w") as f:
        f.write(CONTENT)
    sync("file_oid")


spec = { 'entries': [storage_agent, put] }
