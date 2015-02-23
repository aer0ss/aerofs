# IMPORTANT - Conditions for running this test:
#   -   The first actor needs to be a team server configured to use linked storage
#   -   The second actor does not have to be the same user as the first, but must
#   be in the same organization

import os
import shutil
from . import ts_shared_instance_unique_path
from core.multiroot.teamserver import ts_user_instance_unique_path
from lib.files import instance_unique_path, wait_file_with_content
from lib import ritual
from lib.files.files import wait_path_to_disappear
from syncdet.case import instance_unique_string
from syncdet.case.sync import sync

FILENAME = "iLike2"
CONTENT = "Move it!"

def ts_shared_path():
    return os.path.join(ts_shared_instance_unique_path() + "-share", FILENAME)

def ts_user_path():
    return os.path.join(ts_user_instance_unique_path(), FILENAME)

def client_shared_path():
    return os.path.join(instance_unique_path(), instance_unique_string() + "-share", FILENAME)

def client_user_path():
    return os.path.join(instance_unique_path(), FILENAME)


def teamserver():
    # must wait for shared folder to be created before trying to resolve sid
    sync("shared")

    shared_path = ts_shared_path()
    print 'get', shared_path
    wait_file_with_content(shared_path, CONTENT)

    user_path = ts_user_path()
    print 'move to', user_path
    shutil.move(shared_path, user_path)


def sharer():
    shared_path = client_shared_path()
    print 'put', shared_path
    folder = os.path.dirname(shared_path)
    os.makedirs(folder)
    ritual.connect().share_folder(folder)
    sync("shared")

    with open(shared_path, "w") as f:
        f.write(CONTENT)

    user_path = client_user_path()
    print 'get', user_path
    wait_file_with_content(user_path, CONTENT)
    wait_path_to_disappear(shared_path)

spec = {'entries': [teamserver, sharer]}
