"""
Dumping ground for deduplication of code across similar linker tests

"""
import os
import time
import shutil
from functools import partial

from syncdet.case.sync import sync
from syncdet.case.assertion import assertEqual

from lib import ritual
from lib.network_partition import GlobalNetworkPartition
from lib.files import instance_unique_path, wait_file_with_content, wait_file_new_content
from lib.files.dirtree import wait_for_any


def _move(source, target):
    # this is a bit of a cheat:
    # What we want to reproduce is the presence of META in absence of CONTENT
    # and expel/readmit is simpler/more reliable than interrupting a transfer
    # NOTE: the delay between expel/readmit is a safeguard against deletion of
    # the readmitted folder by notifications caused by the expulsion...
    r = ritual.connect()
    parent = os.path.dirname(target())
    r.exclude_folder(parent)
    time.sleep(.5)
    r.include_folder(parent)

    print 'removed target content'

    print 'move {} -> {}'.format(source(), target())
    shutil.move(source(), target())

def mover(source, target):
    return partial(_move, source, target)

def _creator(dt_base, wait_for_final_state):
    dt_base.write()
    sync(0)

    with GlobalNetworkPartition():
        pass

    wait_for_final_state()

def _mover(dt_base, move, wait_for_final_state):
    wait_for_any(dt_base)
    sync(0)

    with GlobalNetworkPartition():
        move()

    wait_for_final_state()

def _receiver(dt_base, wait_for_final_state):
    wait_for_any(dt_base)
    sync(0)

    with GlobalNetworkPartition():
        pass

    wait_for_final_state()

def update_in_partition_test(dt_base, update, wfs, wfs_updater=None):
    """
    Create the spec for a [2..n]-client test where one actor makes a change in
    a global network partition.
    @param dt_base: Initial DirTree to create
    @param update: update operation
    @param wfs: wait function, should not return until the actor is in a satisfying final state
    @param wfs_updater: if not None, special wait function used by the actor making the update
    """
    return {
        'entries': [
            partial(_creator, dt_base, wfs),
            partial(_mover, dt_base, update, wfs_updater if wfs_updater is not None else wfs)
        ],
        'default': partial(_receiver, dt_base, wfs)
    }


_OLD_CONTENT = 'old content\n'
_NEW_CONTENT = 'new content\n'

def _replace_sender(replace):
    path = instance_unique_path()
    with open(path, 'w') as f: f.write(_OLD_CONTENT)

    time.sleep(1)

    # wait for the receiver to receive the file
    sync(0)

    r = ritual.connect()
    soidOld = r.test_get_object_identifier(path)

    replace(path, _NEW_CONTENT, r)

    sync(1)

    assertEqual(soidOld, r.test_get_object_identifier(path))

def _replace_receiver():
    path = instance_unique_path()
    wait_file_with_content(path, _OLD_CONTENT)

    r = ritual.connect()
    soidOld = r.test_get_object_identifier(path)

    # notify the sender that the file has received
    sync(0)

    wait_file_new_content(path, _NEW_CONTENT)

    sync(1)

    assertEqual(soidOld, r.test_get_object_identifier(path))

def replace_test(replace):
    """
    Create the spec for a [2..n]-client test where one actor changes the
    content of a file and all actors ensure that the OID does not change
    @param replace: function taking a path and a content to write to that path
    """
    return {
        'entries': [
            partial(_replace_sender, replace)
        ],
        'default': _replace_receiver
    }
