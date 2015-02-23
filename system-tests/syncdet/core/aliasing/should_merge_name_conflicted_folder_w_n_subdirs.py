"""
On n devices, in a network partition, create a directory of the same name
(_ALIAS_DIR). On each device, create a unique subdirectory under _ALIAS_DIR.

Wait until AeroFS aliases all instances of _ALIAS_DIR,
i.e. all n unique subdirectories appear under that folder

TODO (MJ) we should have a similar n-client test using subfiles for
aliasing content conflicts

"""
import os

from syncdet.case import actor_id, actor_count

from lib import ritual
from lib.files import dirtree, instance_unique_path
from lib.network_partition import GlobalNetworkPartition


_ALIAS_DIR = "dir"
_SUB_DIR = "subdir"

def main():
    subdir_name = _SUB_DIR + str(actor_id())
    subdir_path = os.path.join(instance_unique_path(), _ALIAS_DIR, subdir_name)

    r = ritual.connect()
    with GlobalNetworkPartition(r):
        os.makedirs(subdir_path)
        r.wait_path(subdir_path)

    # Create a dirtree representing the final state, and wait for it
    # to represent the file system
    dt = dirtree.InstanceUniqueDirTree(
        { _ALIAS_DIR :
              _create_dict_of_empty_subdirs(actor_count())
        })
    dirtree.wait_for_any(dt)

def _create_dict_of_empty_subdirs(n_dirs):
    return dict([(_SUB_DIR + str(i), {}) for i in range(n_dirs)])

spec = { 'default': main }
