"""
First synchronize a file between the two devices, then create a hard link
on the file and delete the original hard link make sure that the other file gets
synced to the other device.

NB: The first actor MUST be able to create symlinks through os.link

Windows does support symlinks under the hood but the version of Python
that we currently use doesn't expose this feature, which implies that
the first actor MUST be a Linux or OSX one.
"""

import os
import time

from syncdet.case import sync, assertion

from lib import files, ritual
from should_ignore_one_hardlink_file import get_paths, assert_unix  # TODO refactor


_FILE_CONTENT = 'hello, world!'


def sender():
    assert_unix()

    path_dir, path_f1, path_f2 = get_paths()

    # create a directory and add file f1 to it
    os.makedirs(path_dir)
    with open(path_f1, "w") as f: f.write(_FILE_CONTENT)

    # wait until receiver gets file f1
    sync.sync(0)

    # create a hard link between f1 and f2
    os.link(path_f1, path_f2)

    # remove the hard link and update the file such that
    # the linux notifier will pick the change in the file.
    # Recreate the hard link between f1 and f2 after f2 gets
    # updated with the new content. In the old system a move from
    # f2 to f1 would have occurred; with the new system only f2 stays
    # in the database.
    # note: a full scan would be normally necessary for
    #       the change to be picked up right away.
    os.remove(path_f1)
    sync.sync(1)

    with open(path_f2, "w") as f: f.write(_FILE_CONTENT)
    os.link(path_f2, path_f1)

    # wait until f2 appears in the receiver
    sync.sync(2)
    assertion.assertTrue(os.path.exists(path_f2))
    assertion.assertTrue(os.path.exists(path_f1))


def receiver():
    path_dir, path_f1, path_f2 = get_paths()

    files.wait_file_with_content(path_f1, _FILE_CONTENT)

    # acknowledge the transfer
    sync.sync(0)

    # make sure the older file was removed from the file system
    files.wait_path_to_disappear(path_f1)

    # acknowledge the transfer
    sync.sync(1)

    files.wait_file_with_content(path_f2, _FILE_CONTENT)
    sync.sync(2)
    assertion.assertTrue(os.path.exists(path_f2))
    assertion.assertFalse(os.path.exists(path_f1))


spec = {'entries': [sender], 'default': receiver}
