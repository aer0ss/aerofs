"""
This test checks if we can share an unlinked folder. In the sharer
we unlinked a folder(external) and try to share it and then make
sure that the sharee still gets an invite.
"""
import os
import sys
import binascii
from lib import ritual
from lib.app.cfg import get_cfg

from syncdet.case.sync import sync
from syncdet.case import instance_unique_string
from syncdet.case.assertion import assertTrue
from common_multiuser import share_external_dir, wait_for_external_shared_folder


def sharer():
    path = os.path.join(get_cfg().get_rtroot(), instance_unique_string())
    os.mkdir(path)

    r = ritual.connect()
    sid = r.create_root(path)
    print binascii.hexlify(sid), path

    # Unlink root and assert that it is unlinked.
    name = os.path.basename(path)
    r.unlink_root(sid)
    assertTrue(name in [pending_root.name for pending_root in r.list_unlinked_roots()])

    # Share with the other actor to make sure sure that the
    # unlinked folder is shareable.
    share_external_dir(sid, ritual.OWNER)
    sync("shared")

def sharee():
    path = os.path.join(get_cfg().get_rtroot(), instance_unique_string())
    sync("shared")
    wait_for_external_shared_folder(path)

spec = { "entries": [sharer], "default": sharee }
