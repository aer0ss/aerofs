"""
This is a test to assert that the shared_folder_list ritual call
returns unlinked roots. This is done by checking if the path
of the pending root is present in the list of paths returned by
ritual service list_shared_folders().

The sharer originally shares a external folder where'as
the sharee, after this event has occurred checks if that
same folder(which is a pending root from the point of the sharee
since its not yet chosen to sync on that device) appears in its
list shared folders list.
"""
import os
import time
import binascii
import sys
from lib import ritual
from aerofs_common.param import POLLING_INTERVAL
from lib.app.cfg import get_cfg
from syncdet.case import instance_unique_string
from syncdet.case.assertion import assertTrue
from syncdet.case.sync import sync

FILENAME = "hello"
CONTENT = "world"

# Creates a folder and shares it.
def sharer():
    path = os.path.join(get_cfg().get_rtroot(), instance_unique_string())
    os.mkdir(path)

    with open(os.path.join(path, FILENAME), 'w') as f:
        f.write(CONTENT)

    sid = ritual.connect().create_root(path)
    print path, binascii.hexlify(sid)

    sync("shared")

# Checks if the shared folder (which hasn't been linked yet) still shows up in the list_shared_folders.
def sharee():
    path = os.path.join(get_cfg().get_rtroot(), instance_unique_string())
    sync("shared")
    r = ritual.connect()

    # Check if the folder is still a part of the pending roots.
    while instance_unique_string() not in set([root.name for root in r.list_unlinked_roots()]):
        time.sleep(POLLING_INTERVAL)
    # Check if the folder now shows up in list shared folders.
    assertTrue(instance_unique_string() in r.list_shared_folders_names())

spec = { "entries": [sharer], "default": sharee }
