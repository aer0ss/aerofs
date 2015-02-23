# IMPORTANT - Conditions for running this test:
#   -   The first actor needs to be a team server configured to use linked storage
#   -   The second actor does not have to be the same user as the first, but must
#   be in the same organization

import os
from . import ts_spec, ts_shared_instance_unique_path
from lib.files import instance_unique_path, wait_file_with_content
from lib import ritual
from syncdet.case.sync import sync

FILENAME = "WeeOoh"
CONTENT = "I look just like Buddy Holly"

def ts_path():
    # must wait for shared folder to be created before trying to resolve sid
    sync("shared")
    return os.path.join(ts_shared_instance_unique_path(), FILENAME)

def client_path():
    return os.path.join(instance_unique_path(), FILENAME)

def get(path):
    print 'get', path
    wait_file_with_content(path, CONTENT)

def put(path):
    print 'put', path

    folder = os.path.dirname(path)
    os.makedirs(folder)
    ritual.connect().share_folder(folder)
    sync("shared")

    with open(path, "w") as f:
        f.write(CONTENT)


spec = ts_spec(teamserver=(get, ts_path), clients=[(put, client_path)])
