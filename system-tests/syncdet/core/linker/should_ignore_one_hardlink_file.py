"""
Create two hard linked files, and make sure that only one of them is propagated
to the other computers.
This case is most useful when run on MacOS or Linux (generally Unix).

NB: The first actor MUST be able to create symlinks through os.link

Windows does support symlinks under the hood but the version of Python
that we currently use doesn't expose this feature, which implies that
the first actor MUST be a Linux or OSX one.
"""

import os
import time

from syncdet.case import sync, assertion

from aerofs_common.param import POLLING_INTERVAL
from lib import files, ritual


dir_name = 'dir'
f_name1 = 'f1'
f_name2 = 'f2'

_FILE_CONTENT = 'hello, world!'


def get_paths():
    path_dir = os.path.join(files.instance_unique_path(), dir_name)
    path_f1 = os.path.join(path_dir, f_name1)
    path_f2 = os.path.join(path_dir, f_name2)
    return path_dir, path_f1, path_f2


def assert_unix():
    assertion.assertTrue(hasattr(os, 'link'), "Can't test hard link behavior on non-Unix platforms")


def sender():
    assert_unix()

    path_dir, path_f1, path_f2 = get_paths()

    # create a directory and add file f1 to it
    os.makedirs(path_dir)
    with open(path_f1, "w") as f:
        f.write(_FILE_CONTENT)

    # wait until receiver gets file f1
    sync.sync(0)

    # create a hard link between f1 and f2
    os.link(path_f1, path_f2)

    # wait until the receiver has potentially received the creation of f2
    sync.sync(1)


def receiver():
    path_dir, path_f1, path_f2 = get_paths()

    # wait until f1 appears under dir/
    files.wait_file_with_content(path_f1, _FILE_CONTENT)

    # acknowledge the transfer
    sync.sync(0)

    # give enough time to potentially transfer f2 onto this device,
    # if the hard link problem is not fixed f2 should appear on this device and not f1
    time.sleep(2)
    sync.sync(1)

    # make sure f1 still exists and f2 never transferred
    assertion.assertTrue(os.path.exists(path_f1))
    assertion.assertFalse(os.path.exists(path_f2))


spec = {'entries': [sender], 'default': receiver}
