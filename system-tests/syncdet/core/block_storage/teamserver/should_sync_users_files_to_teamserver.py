# IMPORTANT - Conditions for running this test:
#   -   The first actor in the config file needs to be a team server
#   -   If the second actor does not have to be the same user as the first,
#       but they must be in the same organization

import os
from syncdet.case import instance_unique_string
from lib.files import instance_unique_path
from core.block_storage import common

FILENAME = "Red"
CONTENT = "The blood of angry men"

def teamserver():
    relative = os.path.join(
        instance_unique_string(),
        FILENAME
    )
    # wait for the file to come
    print 'get', relative
    common.ritual_wait_pbpath_with_content(common.ts_user_path(relative), CONTENT)

def put():
    path = os.path.join(
        instance_unique_path(),
        FILENAME
    )
    print 'put', path
    os.makedirs(instance_unique_path())
    with open(path, "w") as f:
        f.write(CONTENT)

spec = { 'entries': [teamserver, put] }
