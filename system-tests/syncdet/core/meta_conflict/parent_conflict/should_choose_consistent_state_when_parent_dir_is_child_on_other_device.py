"""
Want to create this interaction, where a message says the parent of a
local directory is its child

 r
 |_n1o1
    |_n2o2
       |_n3o3
            n1o1, p=o3
           <------------

How to produce:

  A                    B

create:
 r
 |_n1o1
 |_n2o2
    |_n3o3
         ------->    r
                     |_n1o1
                     |_n2o2
                        |_n3o3

 rename:      |  rename:
 r            |      r
 |_n1o1       |      |_n2o2
    |_n2o2    |         |_n3o3
       |_n3o3 |            |_n1o1
         n2o2, p=o1
         ---------->
          OR
         n1o1, p=o3
         <----------

 final states: (arbitrary conflict resolution by ReceiveAndApplyUpdate)

     r             r              r
     |_n3o3        |_n1o1         |_n3o3
     |_n1o1           |_n2o2         |_n1o1
        |_n2o2           |_n3o3         |_n2o2

 TODO (MJ) wouldn't it be nice to have a final state that was what
 either of the devices actually wanted.
"""

from syncdet.case import sync, actor_id
from syncdet.case.assertion import assertTrue
from lib.files import dirtree, instance_unique_path
from lib.files.dirtree import InstanceUniqueDirTree
from lib.network_partition import NetworkPartition
from lib import ritual
import os
import shutil
import collections

_NAMES = ['d1', 'd2', 'd3']

def renamer():
    actor = actor_id()
    assert 0 <= actor <= 1

    # Create and sync the initial state
    dt = InstanceUniqueDirTree(
        { _NAMES[0] : {},
          _NAMES[1] : { _NAMES[2] : {} }
        })
    if actor == 0: dt.write()
    else: dirtree.wait_for_any(dt)

    sync.sync(0)

    with NetworkPartition():
        sync.sync(1)
        _move_dirs_to_form_nested_dir_tree(actor)
        sync.sync(2)

    _wait_for_final_state()

    sync.sync(3)

def observer():
    with NetworkPartition():
        for i in range(4): sync.sync(i)

    _wait_for_final_state()

def _move_dirs_to_form_nested_dir_tree(actor):
    r = ritual.connect()

    # The second actor should create a different directory tree than the first
    if actor == 0:
        shutil.move(os.path.join(instance_unique_path(), _NAMES[1]),
            os.path.join(instance_unique_path(), _NAMES[0]))
    else:
        shutil.move(os.path.join(instance_unique_path(), _NAMES[0]),
            os.path.join(*([instance_unique_path()] + _NAMES[1:])))


    if actor == 0:
        names = _NAMES
    else:
        names = collections.deque(_NAMES)
        names.rotate(-1)
        names = list(names)

    new_path = os.path.join(*([instance_unique_path()] + names))
    print new_path
    r.wait_path(new_path)

    # Verify correct state on filesystem:
    dt = InstanceUniqueDirTree(_create_nested_dicts_from_list(names))
    assertTrue(dt.represents_fs())

def _create_nested_dicts_from_list(names):
    if not names: return {}
    return { names[0] : _create_nested_dicts_from_list(names[1:]) }

def _wait_for_final_state():
    valid_dts = [
        InstanceUniqueDirTree(
            { _NAMES[2] : {},
              _NAMES[0] : {_NAMES[1] : {} } }),
        InstanceUniqueDirTree(_create_nested_dicts_from_list(_NAMES)),
        InstanceUniqueDirTree(
            _create_nested_dicts_from_list(_NAMES[-1:] + _NAMES[:-1]))
        ]

    dirtree.wait_for_any(*valid_dts)

spec = {'entries': [renamer]*2, 'default': observer}
