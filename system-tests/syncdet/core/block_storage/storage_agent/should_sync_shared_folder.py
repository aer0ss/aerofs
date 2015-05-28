import os
import requests
import time

from aerofs_common import param
from aerofs_sp import sp as sp_service
from core.block_storage.storage_agent import common
from core.sharing.common_multiuser import share_admitted_internal_dir
from lib import ritual
from lib.app.cfg import get_cfg
from lib.files import instance_unique_path
from syncdet import case
from syncdet.case import instance_unique_string
from syncdet.case.assertion import assertEqual
from syncdet.case.sync import sync
from syncdet.actors import actor_list


SHARED_NAME = "share_" + instance_unique_string()
FILENAME = "file_" + instance_unique_string()
CONTENT = "This is sparta"


def get_child_id(parent, object_name, is_child_folder=True):
    API_URL = "https://{}/api/v1.2".format(case.local_actor().aero_host)
    token = common.get_oauth_token_for_user(actor_list()[1])

    while True:
        r = requests.get(API_URL + "/folders/{}/children".format(parent),
            headers={"Authorization": "Bearer " + token},
            params={"fields": "path,children"})
        r.raise_for_status()
        object_type = "folders" if is_child_folder else "files"
        if len(r.json()[object_type]) > 0:
            break;
        time.sleep(param.POLLING_INTERVAL)

    match = [f for f in r.json()[object_type] if f['name'] == object_name]
    return match[0]['id']


def storage_agent():
    API_URL = "https://{}/api/v1.2".format(case.local_actor().aero_host)
    token = common.get_oauth_token_for_user(actor_list()[1])

    sync("shared_folder")

    #Get shared folder id
    folder_id = get_child_id("root", SHARED_NAME)
    file_id = get_child_id(folder_id, FILENAME, False)

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
    shared_path = os.path.join(get_cfg().get_root_anchor(), SHARED_NAME)
    share_admitted_internal_dir(shared_path, ritual.OWNER)
    with open(os.path.join(shared_path, FILENAME), "w") as f:
        f.write(CONTENT)

    print "client"
    sync("shared_folder")


spec = { 'entries': [storage_agent, put] }
