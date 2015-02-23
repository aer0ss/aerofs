"""
This test is intended to generate a name conflict on a folder, containing
different subfiles. The result should be one copy of the folder on all peers,
containing all the subfiles.

"""
# TODO (MJ) verify whether the receiver() workers might actually make the
# test less strict when adding more peers.  I suspect the 'default' task
# should just be creator2 which will make n-1 copies of "file2" in the system
# with differing OID and n OIDs for "folder"
# The aliasing algorithm should be able to handle this.

import os

from syncdet.case import actor_id

from lib import files, ritual
from lib.network_partition import GlobalNetworkPartition


_FILE_NAMES = ("file1", "file2")
_FILE_CONTENTS = ("Mary had a little lamb", "Tony had a gaint goose")


def _folder_path():
    return os.path.join(files.instance_unique_path(), "folder")


def _wait_for_final_state():
    # both files should appear under the same folder
    for n, c in zip(_FILE_NAMES, _FILE_CONTENTS):
        files.wait_file_with_content(os.path.join(_folder_path(), n), c)


def create(folder_path, file_name, file_content):
    os.makedirs(folder_path)
    file = os.path.join(folder_path, file_name)
    with open(file, 'w') as f:
        f.write(file_content)

    r = ritual.connect()
    r.wait_path(file)


def creator():
    id = actor_id()
    with GlobalNetworkPartition():
        create(_folder_path(), _FILE_NAMES[id], _FILE_CONTENTS[id])

    _wait_for_final_state()


def receiver():
    # Need to block on the internal barriers while syncing is paused
    with GlobalNetworkPartition():
        pass
    _wait_for_final_state()


spec = {'entries': [creator, creator], 'default': receiver}
