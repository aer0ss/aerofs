"""
This test checks if we can delete an external shared folder
on leaving it.
We first create a folder, share it and then leave it.
After we leave we ensure that its on longer a root by checking
its children attributes delete it.

"""
import binascii
import os
import sys
import time

from syncdet.case import instance_unique_string

from aerofs_common import convert
from aerofs_common.param import POLLING_INTERVAL
from lib import ritual
from lib.app.cfg import get_cfg

def get_folder_path():
    #creating folder
    path = os.path.join(get_cfg().get_rtroot(), instance_unique_string())

    os.mkdir(path)
    return path

def remove_external_share(path):
    # Delete folder and check if the folder still exists
    os.rmdir(path)
    while os.path.exists(path):
        time.sleep(POLLING_INTERVAL)

def main():
    path = get_folder_path()
    # Link external root and share it.
    r = ritual.connect()
    sid = r.create_root(path)

    # Helpful for debugging
    print path, binascii.hexlify(sid)

    # Leave external root
    pbpath = convert.store_relative_to_pbpath(sid, "")
    r.leave_shared_folder_pb(pbpath)

    # Make sure the path disappears and we can remove that external share/folder.
    r.wait_pbpath_to_disappear(pbpath)
    remove_external_share(path)

spec = { 'entries': [main] }
