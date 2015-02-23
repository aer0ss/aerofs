"""
This module tests that after sharing a folder, the folder and its subfolders
and files stay at the original path.
"""
import os
from lib import ritual
import lib.files
from syncdet.case.assertion import assertTrue

_FILE_CONTENT = "Mary had a little lamb"

def main():
    root = lib.files.instance_unique_path()
    folder = os.path.join(root, "foo", "bar")
    subfolder = os.path.join(folder, "subfolder")
    file = os.path.join(folder, "file")

    # create objects
    os.makedirs(subfolder)
    with open(file, 'w') as f: f.write(_FILE_CONTENT)

    # share the folder
    r = ritual.connect()
    r.share_folder(folder)

    assertTrue(os.path.isdir(subfolder))
    lib.files.wait_file_with_content(file, _FILE_CONTENT)

spec = { 'entries': [main] }