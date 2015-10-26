"""
create shared folder
"""

import os
import time
from lib.files import instance_unique_path
from lib.files.dirtree.dirtree import DirTree
from lib import ritual
from syncdet.case.assertion import assertTrue
from lib.cases import reinstall
from lib.app import aerofs_proc

from ..sharing.common import TAG_FILE_NAME

def shared_folder():
    return os.path.join(instance_unique_path(), "shared")

def sub_shared_folder():
    return os.path.join(shared_folder(), "sub")

def regular_folder():
    return os.path.join(instance_unique_path(), "regular")

def sub_regular_folder():
    return os.path.join(regular_folder(), "sub")

def main():
    os.makedirs(sub_shared_folder())
    os.makedirs(sub_regular_folder())

    r = ritual.connect()
    r.share_folder(shared_folder())
    sf = set(r.list_admitted_or_linked_shared_folders())

    print 'shared'

    aerofs_proc.stop_all()

    # create some invalid tag files
    # TODO: add a valid sid to which the user does not have access
    DirTree('invalid', {
        'empty':    {TAG_FILE_NAME: ""},
        'nothex':   {TAG_FILE_NAME: "#$@%*"},
        'tooshort': {TAG_FILE_NAME: "849CC7A7D87F34D55710C24B0F7F8C"},
        'toolong':  {TAG_FILE_NAME: "849CC7A7D87F34D55710C24B0F7F8C9600"},
        'notsid':   {TAG_FILE_NAME: "849CC7A7D87F44D55710C24B0F7F8C96"}, # 13-th hex digit must be 0 or 3
        'dir':      {TAG_FILE_NAME: {}}
    }).write(instance_unique_path())

    reinstall.reinstall()

    # check that folders are still present after reinstall
    assertTrue(os.path.exists(sub_shared_folder()))
    assertTrue(os.path.exists(sub_regular_folder()))

    # N.B. (JG) Previous tests may have expelled stores, which are not listed under
    # list_admitted_or_linked_shared_folders. When we reinstall, we clear the db and forget that we have
    # expelled them, so the list of shared folders can grow. This test checks that shared
    # folders are restored, which is true iff all shares from before are present after.
    assertTrue(sf.issubset(set(ritual.connect().list_admitted_or_linked_shared_folders())))

    # check that all invalid tag files have been removed
    n = 0
    while not DirTree('invalid', {
                'empty':    {},
                'nothex':   {},
                'tooshort': {},
                'toolong':  {},
                'notsid':   {},
                'dir':      {}
            }).represents(os.path.join(instance_unique_path(), 'invalid'), True):
        time.sleep(0.2)
        n += 1
        if n > 10:
            assertTrue(False)


spec = {'entries': [main]}
