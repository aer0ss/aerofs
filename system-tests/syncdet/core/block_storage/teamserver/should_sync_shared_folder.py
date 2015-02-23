import os
import time

from syncdet.case import instance_unique_string
from syncdet.case.sync import sync

from aerofs_common.param import POLLING_INTERVAL
from core.block_storage import common
from core.sharing.common_multiuser import share_admitted_internal_dir
from lib import ritual
from lib.files import instance_unique_path


FILENAME = "WeeOoh"
CONTENT = "I look just like Buddy Holly"


def teamserver():
    r = ritual.connect()
    num_shares = len(r.list_admitted_or_linked_shared_folders())
    sync("List shared folders")
    # wait until the teamserver autojoins the store before calling wait_pbpath to avoid NOPERM
    while len(r.list_shared_folders()) == num_shares:
        time.sleep(POLLING_INTERVAL)

    sync("Shared Folder Joined")
    common.ritual_wait_pbpath_with_content(common.ts_shared_path(instance_unique_string(), FILENAME), CONTENT)


def put():
    sync("List shared folders")
    share_admitted_internal_dir(instance_unique_path(), ritual.OWNER)
    with open(os.path.join(instance_unique_path(), FILENAME), "w") as f:
        f.write(CONTENT)
    sync("Shared Folder Joined")

spec = {"entries": [teamserver, put]}
