# IMPORTANT - Conditions for running this test:
#   -   The first actor needs to be a team server configured to use linked storage
#   -   The second actor does not have to be the same user as the first, but must
#   be in the same organization

import os
from . import ts_spec, ts_user_instance_unique_path
from lib.files import instance_unique_path, wait_file_with_content

FILENAME = "Red"
CONTENT = "The blood of angry men"

def ts_path():
    return os.path.join(ts_user_instance_unique_path(), FILENAME)

def client_path():
    return os.path.join(instance_unique_path(), FILENAME)

def get(path):
    print 'get', path
    wait_file_with_content(path, CONTENT)

def put(path):
    print 'put', path
    os.makedirs(os.path.dirname(path))
    with open(path, "w") as f:
        f.write(CONTENT)


spec = ts_spec(teamserver=(get, ts_path), clients=[(put, client_path)])
