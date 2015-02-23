"""
When an existing object is renamed to a name that conflicts with an expelled
object, the expelled object should be renamed to make way for the admitted one.

"""
import os
import shutil

import common
from lib import ritual
from lib.files import instance_unique_path
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any


dt_base = InstanceUniqueDirTree({
    'foo': {},
    'bar': {}
})

def source_path():
    return os.path.join(instance_unique_path(), 'foo')

def target_path():
    return os.path.join(instance_unique_path(), 'bar')

def move():
    r = ritual.connect()
    r.exclude_folder(target_path())

    shutil.move(source_path(), target_path())

def wait_for_final_state_mover():
    # final state for actor with expelled object
    wait_for_any(InstanceUniqueDirTree({
        'bar': {}
    }))

def wait_for_final_state():
    # final state for actors with admitted object
    wait_for_any(InstanceUniqueDirTree({
        'bar': {},
        'bar (2)': {}
    }))

spec = common.update_in_partition_test(
    dt_base,
    move,
    wait_for_final_state, wait_for_final_state_mover)

