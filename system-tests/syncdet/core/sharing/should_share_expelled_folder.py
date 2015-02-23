"""
This test checks if we can share an expelled folder. In the sharer
we unlinked a folder(external) and try to share it and then make
sure that the sharee still gets an invite.
"""
import os
import sys
import binascii
from lib import ritual
from lib.files import instance_unique_path
from syncdet.case.sync import sync
from syncdet.case.assertion import assertTrue
from common_multiuser import share_expelled_internal_dir, wait_for_expelled_shared_folder

def sharer():
    path = instance_unique_path()
    os.makedirs(path)
    # share the folder
    r = ritual.connect()
    r.share_folder(path)
    # expel and asser that folder got expelled
    r.exclude_folder(path)
    assertTrue(not os.path.exists(path))
    assertTrue(path in frozenset(r.list_excluded_folders()))

    # Share with the other actor to make sure sure that the
    # unlinked folder is shareable.
    share_expelled_internal_dir(path, ritual.OWNER)
    sync("shared")

def sharee():
    path = instance_unique_path()
    sync("shared")
    wait_for_expelled_shared_folder(path)

spec = { "entries": [sharer], "default": sharee }
