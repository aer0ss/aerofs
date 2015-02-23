"""
This test checks if we can update/delete acls for an unlinked folder. In the sharer
we unlink a folder and try to update and delete its acls and make sure
those get propagated to the sharee.
"""
import os
import sys
import binascii

from lib import ritual
from lib.files import instance_unique_path
from lib.app.cfg import get_cfg
from syncdet.case import instance_unique_string
from syncdet.case.assertion import assertTrue
from syncdet.case.sync import sync
from aerofs_common.convert import store_relative_to_pbpath
from common_multiuser import share_external_dir, wait_for_external_shared_folder, update_dir_acl_pbpath, \
assert_unlinked_folder_acl_changed, kickout_dir_pbpath, wait_unlinked_folder_kicked_out

def sharer():
    path = os.path.join(get_cfg().get_rtroot(), instance_unique_string())
    os.mkdir(path)

    r = ritual.connect()
    sid = r.create_root(path)
    print binascii.hexlify(sid), path

    share_external_dir(sid, ritual.OWNER)
    sync("shared")
    sync("check shared")
    name = os.path.basename(path)

    # Update acl for unlinked folder
    r.unlink_root(sid)
    assertTrue(name in [pending_root.name for pending_root in r.list_unlinked_roots()])

    # Delete other actor from unlinked folder acl
    update_dir_acl_pbpath(store_relative_to_pbpath(sid, ""), ritual.EDITOR)
    sync("update acl")
    sync("check update acl")
    kickout_dir_pbpath(store_relative_to_pbpath(sid, ""))
    sync("delete acl")

def sharee():
    path = os.path.join(get_cfg().get_rtroot(), instance_unique_string())
    sync("shared")
    # Make sure folder got shared
    wait_for_external_shared_folder(path)
    sync("check shared")
    sync("update acl")
    # Make sure acls got updated
    assert_unlinked_folder_acl_changed(path, ritual.EDITOR)
    sync("check update acl")
    sync("delete acl")
    # Make sure acls got updated
    wait_unlinked_folder_kicked_out(path)

spec = { "entries": [sharer], "default": sharee }
