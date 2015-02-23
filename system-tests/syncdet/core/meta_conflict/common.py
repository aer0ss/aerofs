import os
import time

from syncdet.case import sync
from syncdet.case.assertion import assertFalse, assertTrue

from aerofs_common import param
from aerofs_ritual.gen.ritual_pb2 import PBObjectAttributes
from lib import files, ritual


def _original_path():
    return os.path.join(files.instance_unique_path(), "excluded")

def _alternative_path():
    return _original_path() + " (2)"

def _alternative_path_2():
    return _original_path() + " (3)"

def _sender(create_in_place, share):
    """
    @param create_in_place whether to create new folders in place of expelled
    folders or move the new folders into the place of the expelled folders.
    @param share whether to share the folder before expelling it
    """
    path = _original_path()

    r = ritual.connect()

    print "create folder A"
    _create(r, create_in_place, path, 'A')
    # wait until the _subfolder_ is picked up before doing anything
    r.wait_path(os.path.join(path, 'A'))
    if share: r.share_folder(path)

    # wait for the receiver to receive A
    sync.sync(0)

    print "exclude folder A"
    r.exclude_folder(path)
    assertFalse(os.path.exists(path))

    print "create folder B"
    _create(r, create_in_place, path, 'B')
    # wait until the daemon picks up the 2nd folder and renames the 1st folder
    r.wait_path(_alternative_path())
    if share: r.share_folder(path)

    # wait for the receiver to receive B
    sync.sync(1)

    print "exclude folder B"
    r.exclude_folder(path)
    assertFalse(os.path.exists(path))

    print "create folder C"
    _create(r, create_in_place, path, 'C')
    # wait until the daemon picks up the 3nd folder and renames the 2st folder
    r.wait_path(_alternative_path_2())
    if share: r.share_folder(path)

    # wait until the receiver to receive C
    sync.sync(2)

    assertFalse(os.path.exists(_alternative_path()))
    assertFalse(os.path.exists(_alternative_path_2()))
    assertTrue(os.path.exists(os.path.join(_original_path(), 'C')))

def _create(r, create_in_place, parent_path, child_name):
    if create_in_place:
        os.makedirs(os.path.join(parent_path, child_name))
    else:
        tmp_parent_path = parent_path + "-tmp"
        tmp_child_path = os.path.join(tmp_parent_path, child_name)
        os.makedirs(tmp_child_path)
        r.wait_path(tmp_child_path)
        os.rename(tmp_parent_path, parent_path)

def _receiver(share):
    """
    @param share whether to wait for the folder to be shared
    """
    r = ritual.connect()

    _wait(r, share, 'A')
    # notify the sender
    sync.sync(0)

    _wait(r, share, 'B')
    # notify the sender
    sync.sync(1)

    _wait(r, share, 'C')
    # notify the sender
    sync.sync(2)

    files.wait_dir(os.path.join(_alternative_path(), 'A'))
    files.wait_dir(os.path.join(_alternative_path_2(), 'B'))

def _wait(r, share, subdir_name):
    """
    @param share whether to wait for the folder to be shared
    """
    files.wait_dir(os.path.join(_original_path(), subdir_name))
    if share:
        # wait until the folder is shared
        _wait_shared_dir(r)
        # wait until the subdir appears under the shared folder. The above
        # waiting on the subdir may successfully return _before_ the folder is
        # shared by the sending peer.
        files.wait_dir(os.path.join(_original_path(), subdir_name))

def _wait_shared_dir(r):
    while PBObjectAttributes.SHARED_FOLDER != \
            r.get_object_attributes(_original_path()).object_attributes.type:
        time.sleep(param.POLLING_INTERVAL)

def creating_sender():
    _sender(True, False)

def moving_sender():
    _sender(False, False)

def creating_sender_for_shared_folders():
    _sender(True, True)

def receiver():
    _receiver(False)

def receiver_for_shared_folders():
    _receiver(True)
