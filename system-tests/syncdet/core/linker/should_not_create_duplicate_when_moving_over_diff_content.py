"""
When an existing file is moved over another existing file, it should be treated
as a deletion of the source and an update of the target. This is to avoid
creating duplicates, which would happen in the past if the source was moved over
a target that existed only as meta in the DB. The target used to be renamed and
the source moved in its place, which led to file duplications in some cases.

"""
import os
import time

import common
from .. import is_polaris
from aerofs_common import param
from lib import ritual
from lib.files import instance_unique_path
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any_but
from syncdet.case import local_actor


CONTENT_SOURCE = "dwelling"
CONTENT_TARGET = "lurking"


def source_path():
    return os.path.join(instance_unique_path(), 'source')

def target_path():
    return os.path.join(instance_unique_path(), 'sub', 'target')

dt_base = InstanceUniqueDirTree({
    'source': CONTENT_SOURCE,
    'sub': { 'target': CONTENT_TARGET }
})

dt_dup = InstanceUniqueDirTree({
    'sub': {
        'target': CONTENT_SOURCE,
        'target (2)': CONTENT_TARGET
    }
})


def _wait_for_final_state(dt):
    wait_for_any_but(dt_dup, dt)
    print 'phy ok'


def _wait_for_conflict():
    r = ritual.connect()
    while not target_path() in r.list_conflicts():
        time.sleep(param.POLLING_INTERVAL)


def wait_for_final_state_mover():
    # final state where the MASTER branch is the updated target file
    _wait_for_final_state(InstanceUniqueDirTree({
        'sub': { 'target': CONTENT_SOURCE }
    }))
    _wait_for_conflict()


def wait_for_final_state():
    # final state where the MASTER branch is the unchanged target file
    _wait_for_final_state(InstanceUniqueDirTree({
        'sub': { 'target': CONTENT_TARGET }
    }))
    # conflicts do not propagate through Polaris
    if not is_polaris():
        _wait_for_conflict()

spec = common.update_in_partition_test(
    dt_base,
    common.mover(source_path, target_path),
    wait_for_final_state, wait_for_final_state_mover)

