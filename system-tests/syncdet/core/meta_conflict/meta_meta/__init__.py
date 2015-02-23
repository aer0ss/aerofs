import os
import time
import shutil
from functools import partial

from syncdet.case import sync

from lib import ritual
from lib.files import instance_unique_path
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any
from lib.network_partition import GlobalNetworkPartition


def move(r, src, dst):
    src_path = os.path.join(instance_unique_path(), src)
    dst_path = os.path.join(instance_unique_path(), dst)
    shutil.move(src_path, dst_path)
    r.wait_path_to_disappear(src_path)
    r.wait_path(dst_path)


def delete(r, src):
    path = os.path.join(instance_unique_path(), src)
    os.remove(path)
    r.wait_path_to_disappear(path)


def wait_for_final_state(final):
    # TODO: need to make sure the daemons have synced (syncstat to the rescue?)
    time.sleep(5)
    wait_for_any(*[InstanceUniqueDirTree(dt) for dt in final])


def _creator(initial, final, op):
    InstanceUniqueDirTree(initial).write()
    sync.sync(0)
    with GlobalNetworkPartition():
        op(ritual.connect())
    wait_for_final_state(final)


def _renamer(initial, final, op):
    wait_for_any(InstanceUniqueDirTree(initial))
    sync.sync(0)
    with GlobalNetworkPartition():
        op(ritual.connect())
    wait_for_final_state(final)


def _observer(initial, final):
    wait_for_any(InstanceUniqueDirTree(initial))
    sync.sync(0)
    with GlobalNetworkPartition():
        pass
    wait_for_final_state(final)


def meta_meta_spec(**kwargs):
    ops = kwargs["ops"]
    entries = [partial(_creator, kwargs["initial"], kwargs["final"], ops[0])]
    del ops[0]
    entries.extend([partial(_renamer, kwargs["initial"], kwargs["final"], op) for op in ops])
    return {
        'entries': entries,
        'default': partial(_observer, kwargs["initial"], kwargs["final"])
    }
