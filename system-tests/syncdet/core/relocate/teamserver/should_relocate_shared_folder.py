# IMPORTANT - Conditions for running this test:
#   -   The first actor in the config file needs to be a team server configured to use linked storage
#   -   If the second actor does not have to be the same user as the first,
#       but they must be in the same organization


import os
from binascii import unhexlify
from . import relocate
from core.multiroot.teamserver import get_instance_unique_sid, ts_shared_instance_unique_path
from lib.files import instance_unique_path, wait_dir
from lib import ritual
from syncdet.case.sync import sync


def teamserver():
    sid = unhexlify(get_instance_unique_sid())
    root = ts_shared_instance_unique_path()

    # wait for the shared folder to appear on the FS before trying to relocate it
    wait_dir(root)

    relocate(root + "_moved", sid)
    relocate(root, sid)


def sharer():
    folder = instance_unique_path()
    os.makedirs(folder)
    ritual.connect().share_folder(folder)


spec = {'entries': [teamserver, sharer]}
