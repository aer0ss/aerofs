"""
When a file gets created and then immediately deleted, the linker might not get
around to checking it out until it is already gone. HdMightCreateNotification
simply ignores ExNotFound errors to avoid frequent rescans especially with the
editors which create and delete temporary files in quick successions.

Testing for ExNotFound ignorance has been done with JUnit. This test verifies
that AeroFS correctly handles unrelated file changes while other files are
getting created and deleted in quick succession.

Note that this test is useful only on platforms where HdMightCreateNotification
is used.

"""
import os

import lib.files
from lib import ritual


def main(delete):
    """
    @param delete whether to delete the file or rename the file
    """
    root = lib.files.instance_unique_path()
    os.mkdir(root)

    # create the root directory
    r = ritual.connect()
    r.wait_path(root)

    f_before = os.path.join(root, "fBefore")
    f_after = os.path.join(root, "fAfter")
    f = os.path.join(root, "f")
    f_new_name = os.path.join(root, "f_new_name")

    create_file(f_before)
    create_file(f)
    if delete: os.remove(f)
    else: os.rename(f, f_new_name)
    create_file(f_after)

    # make sure both files are picked up correctly by the daemon
    r.wait_path(f_before)
    r.wait_path(f_after)
    if not delete: r.wait_path(f_new_name)

def create_file(path):
    with open(path, 'w') as f: f.write("")

spec = { 'entries': [lambda: main(True)] }
