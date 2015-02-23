"""
This test checks if we can update/delete acls for an expelled folder. In the sharer
we expel a folder and try to update and delete its acls it and make sure
those get propagated to the sharee.
"""
import os
import sys
import time
import binascii

from lib import ritual
from lib.files import instance_unique_path
from syncdet.case.sync import sync
from syncdet.case.assertion import assertTrue
from common_multiuser import share_admitted_internal_dir, wait_for_expelled_shared_folder, update_dir_acl,\
assert_expelled_folder_acl_changed, wait_for_admitted_shared_folder, kickout_dir, wait_expelled_folder_kicked_out

def sharer():
    path = instance_unique_path()
    os.makedirs(path)

    # share the folder
    r = ritual.connect()
    share_admitted_internal_dir(path, ritual.OWNER)
    sync("shared")
    sync("check shared")
    # expel and asser that folder got expelled
    r.exclude_folder(path)
    assertTrue(not os.path.exists(path))
    assertTrue(path in frozenset(r.list_excluded_folders()))

    # Update acl for expelled folder
    update_dir_acl(path, ritual.EDITOR)
    sync("update acl")
    sync("check update acl")
    # Delete other actor from expelled folder acl
    kickout_dir(path)
    sync("delete acl")

def sharee():
    path = instance_unique_path()
    sync("shared")
    # Make sure folder got shared
    wait_for_admitted_shared_folder(path)
    sync("check shared")
    sync("update acl")
    # Make sure acls got updated
    assert_expelled_folder_acl_changed(path, ritual.EDITOR)
    sync("check update acl")
    sync("delete acl")
    # Make sure folder got deleted for this actor
    wait_expelled_folder_kicked_out(path)

spec = { "entries": [sharer], "default": sharee }
