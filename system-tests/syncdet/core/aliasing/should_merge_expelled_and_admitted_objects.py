"""
Aliasing should work even if one of the aliasing pair is admitted and the other
is expelled. The exact result depends on which object is aliased.
"""
# TODO (MJ) refactor out worker() method and use common.no_syncing_worker

import os

from syncdet.case import sync

from .. import wait_aliased
from lib import ritual
from lib.files import instance_path
from lib.network_partition import GlobalNetworkPartition


_FILE_NAME = "file"
_FILE1_CONTENT = "Mary had a little lamb"
_FILE2_CONTENT = "Tony had a gaint goose"


def root_path():
    return instance_path()


def folder_path():
    return instance_path("folder")


def worker(file_content, expel):
    with GlobalNetworkPartition():
        create(_FILE_NAME, file_content, expel)

    wait_aliased("alias", ritual.connect(), instance_path("folder", _FILE_NAME), 2)

    # TODO (WW) re-include the folder afterward, and then modify the file on one
    # machine. there was a bug causing AE with this sequence
    # (because aliasing didn't remove the local tick when moving aliasing KML to target.)


def create(file_name, file_content, expel):
    r = ritual.connect()
    folder = folder_path()
    file = os.path.join(folder_path(), file_name)
    os.makedirs(folder)
    with open(file, 'w') as f:
        f.write(file_content)
    r.wait_path(file)

    if expel:
        r.exclude_folder(folder_path())


def creator1(expel):
    os.makedirs(root_path())

    # wait for other peers to receive the root path
    sync.sync(0)

    worker(_FILE1_CONTENT, expel)


def creator2(expel):
    r = ritual.connect()
    r.wait_path(root_path())

    # notify creator1 that we've received the root path
    sync.sync(0)

    worker(_FILE2_CONTENT, expel)


def receiver():
    r = ritual.connect()
    r.wait_path(root_path())

    # notify creator1 that we've received the root path
    sync.sync(0)

    with GlobalNetworkPartition():
        pass


spec = {'entries': [lambda: creator1(True), lambda: creator2(False)], 'default': receiver}
