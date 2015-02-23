# IMPORTANT - Conditions for running this test:
#   -   The first actor in the config file needs to be a team server
#   -   The second actor does not have to be the same user as the first,
#       but they must be in the same organization

import os
from syncdet.case import instance_unique_string
from lib.files import instance_unique_path, wait_file_with_content
from core.block_storage import common

FILENAME = "Black"
CONTENT = "The dark of ages past"

def teamserver():
    # get the root sid of the second actor
    path = instance_unique_string()
    filename = os.path.join(path, FILENAME)
    # wait for the file to come
    print 'put', filename
    common.ritual_mkdir_pbpath(common.ts_user_path(path))
    common.ritual_write_pbpath(common.ts_user_path(filename), CONTENT)

def get():
    path = os.path.join(
        instance_unique_path(),
        FILENAME
    )
    print 'get', path
    wait_file_with_content(path, CONTENT)

spec = { 'entries': [teamserver, get] }
