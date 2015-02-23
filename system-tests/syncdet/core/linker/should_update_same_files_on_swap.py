"""
When two files are swapped, the path<->OID mapping should not change
but each object should be updated

"""
import os
import shutil
import time

from syncdet.case.assertion import assertEqual

import common
from lib import ritual
from lib.app import aerofs_proc
from lib.files import instance_unique_path
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any


# NB: use different file sizes to ensure changes are detected regardless of timestamps
dt_base = InstanceUniqueDirTree({
    'foo': 'foo',
    'bar': 'bar baz'
})

dt_final = InstanceUniqueDirTree({
    'foo': 'bar baz',
    'bar': 'foo'
})

def path_foo():
    return os.path.join(instance_unique_path(), 'foo')

def path_bar():
    return os.path.join(instance_unique_path(), 'bar')

def path_tmp():
    return os.path.join(instance_unique_path(), 'tmp')

def swap():
    r = ritual.connect()
    soidFoo = r.test_get_object_identifier(path_foo())
    soidBar = r.test_get_object_identifier(path_bar())

    aerofs_proc.stop_all()

    # swap the two files
    shutil.move(path_foo(), path_tmp())
    shutil.move(path_bar(), path_foo())
    shutil.move(path_tmp(), path_bar())

    aerofs_proc.run_ui()

    # TODO: wait for the daemon to react to the changes
    time.sleep(1)

    # check that the SOIDs are unchanged
    r = ritual.connect()
    assertEqual(soidFoo, r.test_get_object_identifier(path_foo()))
    assertEqual(soidBar, r.test_get_object_identifier(path_bar()))

def wait_for_final_state():
    wait_for_any(dt_final)

spec = common.update_in_partition_test(dt_base, swap, wait_for_final_state)
