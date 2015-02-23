"""
This test starts by sharing a directory on two devices, then pauses syncing to
create an interesting dependency: the original directory is moved under a new
directory with its former name. Then syncing is resumed. See the following
diagram for an example.

    A                             B

   {n1o1}                       {n1o1}

  n1o1->n2o1         |
 new file n1o2       |
                     |
 {n2o1, n1o2}        |
                     |
n2o1 -> (n1/n2)o1    |
                     |
{(n1/n2)o1, n1o2}    |

  2 message types
              n1o2 or (n1/n2)o1
              ---------------->
"""

import os
from lib import files
from lib import ritual
from lib.network_partition import GlobalNetworkPartition, _POST_PAUSE, \
    _PRE_RESUME
from syncdet.case import sync
from syncdet.case.assertion import assertFalse, assertEqual

_CHILD_DIR_NAME = 'child'
_CONFLICT_RESOLVED = 'conflict has been resolved'
_PARENT_SYNCED = 'parent path has synced'

def parent_path():
    return os.path.join(files.instance_unique_path(), 'parent')

def upper_child():
    """
    @return the path of the 'child' dir before it is under the parent
    """
    return os.path.join(files.instance_unique_path(), _CHILD_DIR_NAME)

def lower_child():
    """
    @return the path of the 'child' dir when it is under the paper
    """
    return os.path.join(parent_path(), _CHILD_DIR_NAME)

def create_child_then_move_under_parent(r):
    # Rename the parent dir to 'child'
    os.rename(parent_path(), upper_child())
    r.wait_path(upper_child())

    # Create a new parent dir (creating a new OID for it)
    os.makedirs(parent_path())

    # Move the child dir under the new parent
    os.rename(upper_child(), lower_child())

    # Verifications before we move onward
    r.wait_path(lower_child())
    r.wait_path_to_disappear(upper_child())

def wait_for_final_state():
    files.wait_dir(lower_child())
    assertFalse(os.path.exists(upper_child()))
    assertEqual(os.listdir(lower_child()), [])

def mover():
    os.makedirs(parent_path())

    sync.sync(_PARENT_SYNCED)

    r = ritual.connect()

    with GlobalNetworkPartition(r):
        create_child_then_move_under_parent(r)

    wait_for_final_state()
    # signal that the lower child directory exists
    sync.sync(_CONFLICT_RESOLVED)

def receiver():
    files.wait_dir(parent_path())

    sync.sync(_PARENT_SYNCED)

    with GlobalNetworkPartition(): pass

    wait_for_final_state()
    # signal that the lower child directory exists
    sync.sync(_CONFLICT_RESOLVED)

def non_aliaser():
    """
    These are the default peers. They wait for the mover and receiver to
    resolve the name conflict.
    """
    r = ritual.connect()
    r.pause_syncing()

    for s in (_PARENT_SYNCED, _POST_PAUSE, _PRE_RESUME, _CONFLICT_RESOLVED):
        sync.sync(s)

    r.resume_syncing()

    wait_for_final_state()

spec = { 'entries': [mover, receiver], 'default': non_aliaser }
