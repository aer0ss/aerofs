# IMPORTANT - Conditions for running this test:
#   -   The first actor needs to be a team server configured to use linked storage
#   -   The second actor does not have to be the same user as the first, but must
#   be in the same organization

import os
import sys
import time
import binascii
from . import user_id, ts_user_instance_unique_path, ts_shared_instance_unique_path
from lib.files import instance_unique_path, wait_dir, wait_file_with_content, wait_path_to_disappear
from lib import ritual
from lib.app.cfg import get_cfg
from syncdet.case import instance_unique_string
from syncdet.case.sync import sync
from aerofs_common.convert import store_relative_to_pbpath
from aerofs_ritual.ritual import EDITOR
from ...sharing.common_multiuser import wait_for_admitted_shared_folder, FILENAME, FILE_CONTENTS


def teamserver():
    path = os.path.join(get_cfg().get_rtroot(), instance_unique_string())
    os.mkdir(path)

    with open(os.path.join(path, FILENAME), 'w') as f:
        f.write(FILE_CONTENTS)

    r = ritual.connect()
    sid = r.create_root(path)
    print binascii.hexlify(sid), path

    r.share_pbpath(store_relative_to_pbpath(sid, ""), acl={user_id(): EDITOR})

    sync("shared")

    wait_dir(ts_user_instance_unique_path())

    sync("synced")

    wait_path_to_disappear(ts_user_instance_unique_path())
    print 'anchor disappeared'

    time.sleep(5)

    # make sure the shared folder is untouched
    wait_file_with_content(os.path.join(path, FILENAME), FILE_CONTENTS)


def sharer():
    sync("shared")

    wait_for_admitted_shared_folder(instance_unique_path())

    sync("synced")

    ritual.connect().leave_shared_folder(instance_unique_path())


spec = {'entries': [teamserver, sharer]}
