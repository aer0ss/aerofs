"""
When an existing file is moved over another existing file, it should be treated
as a deletion of the source and an update of the target. This is to avoid
creating duplicates, which would happen in the past if the source was moved over
a target that existed only as meta in the DB. The target used to be renamed and
the source moved in its place, which led to file duplications in some cases.
"""
import os
from lib.files import instance_unique_path
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any_but
import common

CONTENT = "dwelling"

def source_path():
    return os.path.join(instance_unique_path(), 'source')

def target_path():
    return os.path.join(instance_unique_path(), 'sub', 'target')

dt_base = InstanceUniqueDirTree({
    'source': CONTENT,
    'sub': { 'target': CONTENT }
})

dt_final = InstanceUniqueDirTree({
    'sub': { 'target': CONTENT }
})

dt_dup = InstanceUniqueDirTree({
    'sub': {
        'target': CONTENT,
        'target (2)': CONTENT
    }
})

def wait_for_final_state():
    wait_for_any_but(dt_dup, dt_final)

spec = common.update_in_partition_test(
    dt_base,
    common.mover(source_path, target_path),
    wait_for_final_state)
