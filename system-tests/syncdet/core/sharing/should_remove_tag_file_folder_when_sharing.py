"""
When sharing a folder, AeroFS creates a tag file ".aerofs" under that folder.
If a folder happens to exist in place of the tag file, the folder should be
removed first. Sigh, users are capricious.
"""

import os
from lib import ritual
import lib.files
from lib.app import aerofs_proc
from syncdet.case.assertion import assertTrue
from common import TAG_FILE_NAME

def main():
    root = lib.files.instance_unique_path()
    folder = os.path.join(root, "shared")
    tag = os.path.join(folder, TAG_FILE_NAME)
    folder_under_tag = os.path.join(tag, "folder under tag")

    aerofs_proc.stop_all()

    # create objects. the tag is created as a non empty directory
    os.makedirs(folder_under_tag)

    aerofs_proc.run_ui()

    # share the folder
    r = ritual.connect()
    r.share_folder(folder)

    # verify the tag folder has been replaced with the tag file
    assertTrue(os.path.isfile(tag))

spec = { 'entries': [main] }