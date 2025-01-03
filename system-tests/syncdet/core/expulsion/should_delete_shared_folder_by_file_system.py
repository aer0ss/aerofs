"""
This tests whether the core can handle deletion of a shared folder from the
file system, unlike most (all?) other tests which delete the folder via
Ritual's expulsion api.

"""
import os

from lib.app.install import rm_rf

from syncdet.case.assertion import assertFalse
from syncdet.case import sync

from lib import files, ritual


def _subdir_path():
    return os.path.join(files.instance_unique_path(), 'subdir')

def deleter():
    r = ritual.connect()
    os.makedirs(_subdir_path())

    # Create a shared folder, then delete it from the file system,
    # not ritual's expulsion
    r.share_folder(files.instance_unique_path())

    sync.sync("shared")

    rm_rf(files.instance_unique_path())

    r.wait_path_to_disappear(files.instance_unique_path())
    assertFalse(os.path.exists(files.instance_unique_path()))

def receiver():
    # Wait for the shared folder and subdir, then signal receipt
    files.wait_dir(_subdir_path())
    sync.sync("shared")

    files.wait_path_to_disappear(files.instance_unique_path())


spec = {'entries': [deleter], 'default': receiver}
