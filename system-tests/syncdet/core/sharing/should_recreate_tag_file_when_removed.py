"""
AeroFS fixes tag files even if the user tries to mess with them
"""

import os
from lib import ritual
import lib.files
from common import TAG_FILE_NAME
from lib.app import aerofs_proc


def shared_folder():
    root = lib.files.instance_unique_path()
    return os.path.join(root, "shared")


def main():
    folder = shared_folder()

    # create objects. the tag is created as a non empty directory
    os.makedirs(folder)

    # share the folder
    r = ritual.connect()
    r.share_folder(folder)

    tag = os.path.join(shared_folder(), TAG_FILE_NAME)

    # wait for the tag file to appear
    lib.files.wait_file(tag)

    # stop AeroFS to avoid race-conditions
    aerofs_proc.stop_all()

    # remove tag
    os.remove(tag)

    # restart
    aerofs_proc.run_ui()

    # wait until the tag file reappears
    lib.files.wait_file(tag)


spec = { 'entries': [main] }
