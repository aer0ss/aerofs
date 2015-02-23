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

    # replace it with a non-empty folder
    folder_under_tag = os.path.join(tag, "folder under tag")
    os.remove(tag)
    os.makedirs(folder_under_tag)

    # restart
    aerofs_proc.run_ui()

    # wait until the tag folder is replaced by a tag file
    lib.files.wait_path_to_disappear(folder_under_tag)
    lib.files.wait_file(tag)


spec = { 'entries': [main] }
