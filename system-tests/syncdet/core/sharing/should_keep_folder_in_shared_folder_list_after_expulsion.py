import os
import time
from lib import ritual
from lib.files import instance_unique_path
from syncdet.case.assertion import assertTrue

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
    # Assert that expelled folder is still part of shared folders list.
    assertTrue(folder in r.list_shared_folders())

spec = { 'entries': [main] }
