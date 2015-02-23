import os
import time

from syncdet.case.assertion import assertTrue

from aerofs_common.param import POLLING_INTERVAL
from lib import ritual
from lib.files import instance_unique_path


def main():
    folder = instance_unique_path()

    # create objects
    os.makedirs(folder)

    # share the folder
    r = ritual.connect()
    r.share_folder(folder)

    # expel
    r.exclude_folder(folder)
    assertTrue(not os.path.exists(folder))
    assertTrue(folder in frozenset(r.list_excluded_folders()))

    # leave the folder
    r.leave_shared_folder(folder)

    # wait for leave to be effective...
    while folder in frozenset(r.list_excluded_folders()):
        time.sleep(POLLING_INTERVAL)


spec = { 'entries': [main] }
