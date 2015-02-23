"""
This test creates a name conflict on both "folder" and its single subfile
"file". It should result in one file with two conflict branches.

"""
import os
import time

import lib.files
from .. import is_polaris
from lib import ritual
from lib.network_partition import GlobalNetworkPartition
from lib.files import instance_path
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any
from syncdet.case.sync import sync_ng


_FILE_NAME = "file"
_FILE1_CONTENT = "Mary had a little lamb"
_FILE2_CONTENT = "Tony had a giant goose"


def folder_path():
    return instance_path("folder")


def _wait_for_conflicts(r):
    while True:
        reply = r.get_object_attributes(instance_path("folder", _FILE_NAME))
        if len(reply.object_attributes.branch) == 2:
            break
        # no point hammering the daemon with Ritual calls
        time.sleep(0.2)


def wait_for_final_state(r):
    if is_polaris():
        p = instance_path("folder", _FILE_NAME)
        sync_ng("conflict",
                validator=lambda votes: sum(votes.itervalues()) == 1,
                vote=lambda: 1 if len(r.get_object_attributes(p).object_attributes.branch) > 1 else None)
    else:
        # we should eventually receive two conflict branches
        _wait_for_conflicts(r)


def create(r, file_name, file_content):
    os.makedirs(folder_path())
    file = os.path.join(folder_path(), file_name)
    with open(file, 'w') as f:
        f.write(file_content)
    r.wait_path(file)


def creator1():
    r = ritual.connect()
    with GlobalNetworkPartition(r):
        create(r, _FILE_NAME, _FILE1_CONTENT)
    wait_for_final_state(r)


def creator2():
    r = ritual.connect()
    with GlobalNetworkPartition(r):
        create(r, _FILE_NAME, _FILE2_CONTENT)
    wait_for_final_state(r)


def receiver():
    with GlobalNetworkPartition():
        pass

    if is_polaris():
        wait_for_any(
            InstanceUniqueDirTree({"folder": {_FILE_NAME: _FILE1_CONTENT}}),
            InstanceUniqueDirTree({"folder": {_FILE_NAME: _FILE2_CONTENT}})
        )
    else:
        _wait_for_conflicts(ritual.connect())


# Timeout is set to 65. Most runs take either 31 seconds or 61 seconds. 65 gives us a good cap.
spec = {'entries': [creator1, creator2], 'default': receiver, 'timeout': 65}
