"""
On Linux at least, i-node numbers (FIDs) are reused when a file deletion is
immediately followed by file creation. If the deleted object is a folder and the
created one is a file, or vice versa, AeroFS should handle the case properly.
The exact handling depends on the situation, e.g., whether the two objects have
the same name, and whether there are other objects with conflicting names.

This test case tests a basic scenario where two objects share the same name and
there is no other interfering objects. This case is most useful when run on
Linux.

"""

import os
import time

from syncdet.case import sync

from aerofs_common.param import POLLING_INTERVAL
from lib import files, ritual


_FILE_CONTENT = 'hahahaha'

def folder_path():
    return os.path.join(files.instance_unique_path(), "path")

def sender():
    path = folder_path()

    # create a folder
    os.makedirs(path)

    # wait for other peers to receive the folder
    sync.sync(0)

    # pause the linker so that it will pick up the deletion and creation below
    # in a single scan.
    r = ritual.connect()
    r.test_pause_linker()

    # delete the folder and then create a file of the same name
    os.rmdir(path)
    with open(path, "w") as f: f.write(_FILE_CONTENT)

    # sleep a bit to prevent file change notifications from entering the linker.
    time.sleep(1)
    r.test_resume_linker()

def receiver():
    path = folder_path()

    files.wait_dir(path)

    # notify the sender
    sync.sync(0)

    while os.path.isdir(path): time.sleep(POLLING_INTERVAL)

    files.wait_file_with_content(path, _FILE_CONTENT)

    # AeroFS must delete the logical folder, instead of rename it. If rename
    # occurs, the parent folder would contain the file as well as the renamed
    # object. The following code verify that eventually AeroFS deletes the
    # folder and only the file remains.
    r = ritual.connect()
    while len(r.get_children_attributes(os.path.dirname(path))) is not 1:
        time.sleep(POLLING_INTERVAL)

spec = {'entries':[sender], 'default':receiver}
