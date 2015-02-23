"""
This test verifies that we properly unlink an external shared folder (aka root).
By properly unlink we mean, it deletes the external shared folder and
its contents, and adds the folder to the pending root DB.
"""
import binascii
import os
import sys

from syncdet.case import instance_unique_string
from syncdet.case.assertion import assertTrue
from lib import ritual
from lib.app.cfg import get_cfg

def get_folder_path():
    #creating folder
    path = os.path.join(get_cfg().get_rtroot(), instance_unique_string())

    os.mkdir(path)

    return path

def main():
    path = get_folder_path()
    # Link external root and share it.
    r = ritual.connect()
    sid = r.create_root(path)
    name = os.path.basename(path)

    # Helpful for debugging
    print path, binascii.hexlify(sid)

    # Unlink external root
    r.unlink_root(sid)

    # Make sure that the  status of the external root becomes pending root and that it
    # is still returned in the shared folder list.
    assertTrue(name in [pending_root.name for pending_root in r.list_unlinked_roots()])
    assertTrue(name in r.list_shared_folders_names())

spec = { 'entries': [main] }
