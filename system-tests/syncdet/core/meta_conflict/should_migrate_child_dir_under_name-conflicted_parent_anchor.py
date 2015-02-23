"""
This script tests the resolution of a name conflict between an anchor and its
(to-be migrated) subfolder.

A folder 'n' is synced between two devices, A and B. During a network
partition: on device A, 'n' is renamed to 'n2'.  A new folder 'n' is created,
and that new 'n' is converted to a shared folder. The old folder 'n2' is
moved into the shared folder 'n' (this performs migration of 'n2' into
store 'n'. The network partition disappears, and device B must resolve the
name conflict between the to-be child folder and the anchor 'n' it just
learned about.
"""

import os
from lib import files
from lib import ritual
from lib.network_partition import GlobalNetworkPartition
from syncdet.case import sync

_CHILD_DIR_NAME = 'child'
_SUBFILE_NAME = 'file'
_FILE_CONTENT = 'this file duplicates one in Aliasing but is easy to read'

def parent_path():
    return os.path.join(files.instance_unique_path(), 'conflict')

def upper_child_path():
    return os.path.join(files.instance_unique_path(), _CHILD_DIR_NAME)

def lower_child_path():
    return os.path.join(parent_path(), _CHILD_DIR_NAME)

def wait_for_final_state():
    files.wait_file_with_content(
        os.path.join(lower_child_path(), _SUBFILE_NAME), _FILE_CONTENT)
    files.wait_path_to_disappear(upper_child_path())

def migrator():
    os.makedirs(parent_path())
    with open(os.path.join(parent_path(), _SUBFILE_NAME), 'w') as f:
        f.write(_FILE_CONTENT)

    sync.sync(0)

    r = ritual.connect()

    with GlobalNetworkPartition(r):
        # Rename the conflict dir to 'child'
        os.rename(parent_path(), upper_child_path())
        r.wait_path(upper_child_path())

        # Create a new parent dir (creating a new OID for it)
        os.makedirs(parent_path())
        # Convert it to a shared folder
        r.share_folder(parent_path())

        # Migrate the child dir under the new shared folder
        os.rename(upper_child_path(), lower_child_path())

        # Verifications before we move onward
        r.wait_path(os.path.join(lower_child_path(), _SUBFILE_NAME))
        r.wait_path_to_disappear(upper_child_path())

    # wait for other peer to resolve the name conflict
    sync.sync(1)

    # ensure nothing changed on this end
    wait_for_final_state()

def receiver():
    files.wait_file_with_content(os.path.join(parent_path(), _SUBFILE_NAME),
        _FILE_CONTENT)

    # Signal that we received the folder and subfile
    sync.sync(0)

    with GlobalNetworkPartition(): pass

    # resolve the name conflict
    wait_for_final_state()

    # signal that the resolution was successful
    sync.sync(1)


spec = {'entries': [migrator], 'default': receiver}