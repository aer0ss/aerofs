"""
The intention here is to verify that after device d3 learns that d2 does not
have the content of a file, d3 can then later download the file from d2 once
the latter gets the content.

Implementation Detail Spoiler Alert: testing that after sending ExNotFound
to a peer, that former peer can later provide the content.

The story line:

       d1                         d2                           d3
                                                    |
    create d/f                                      |
                   d/f                              |
                --------->                          |
                                 d/f                |
                    |                               |
                    |          exclude 'd'          |
                    |                          KML for 'd/f'
                    |                         ------------->
                    |
                    |                               |
                    |                               |
                               include 'd'          |
                   d/f                              |
                --------->                          |
                                 d/f                |
                    |                               |
                    |
                    |                              d/f
                    |                         ------------->
                    |                                         d/f

"""
import os
import time

from syncdet.case import sync
from syncdet.case.assertion import assertFalse

from lib import files, ritual
from lib.network_partition import NetworkPartition


_FILE_CONTENT = "Tell me quickly what's the story. Who saw what and why and " \
                "where. Let him give a full description. Let him answer to " \
                "Javert!"

_BARRIER_RETRIEVER_PARTITION = "retriever is partitioned pre-init"
_BARRIER_FILE_INIT = "file shared"
_BARRIER_FILE_EXPELLED = "file expelled"
_BARRIER_VERSION_RECEIVED = "versions received"
_BARRIER_FILE_READMIT = "file re-admitted"
_BARRIER_SOURCER_PARTITION = "sourcer is partitioned post re-admit"
_BARRIER_FILE_RECEIVED_BY_RETRIEVER = "file received by retriever"

def file_path():
    return os.path.join(files.instance_unique_path(), 'd', 'f')

def sourcer():
    sync.sync(_BARRIER_RETRIEVER_PARTITION)

    # Create directory and file after the retriever peer is nw-partitioned
    os.makedirs(os.path.dirname(file_path()))
    with open(file_path(), 'w') as f: f.write(_FILE_CONTENT)

    sync.sync(_BARRIER_FILE_INIT)

    with NetworkPartition():
        # Once self-partitioned, wait til the file is expelled on the expeller
        sync.sync(_BARRIER_FILE_EXPELLED)
        sync.sync(_BARRIER_VERSION_RECEIVED)

    sync.sync(_BARRIER_FILE_READMIT)

    with NetworkPartition():
        sync.sync(_BARRIER_SOURCER_PARTITION)
        sync.sync(_BARRIER_FILE_RECEIVED_BY_RETRIEVER)

def expeller():
    sync.sync(_BARRIER_RETRIEVER_PARTITION)

    files.wait_file_with_content(file_path(), _FILE_CONTENT)

    # Signal that the file was received
    sync.sync(_BARRIER_FILE_INIT)

    r = ritual.connect()
    r.exclude_folder(os.path.dirname(file_path()))

    sync.sync(_BARRIER_FILE_EXPELLED)
    sync.sync(_BARRIER_VERSION_RECEIVED)

    r.include_folder(os.path.dirname(file_path()))
    files.wait_file_with_content(file_path(), _FILE_CONTENT)

    sync.sync(_BARRIER_FILE_READMIT)
    sync.sync(_BARRIER_SOURCER_PARTITION)
    sync.sync(_BARRIER_FILE_RECEIVED_BY_RETRIEVER)

def retriever():
    with NetworkPartition():
        sync.sync(_BARRIER_RETRIEVER_PARTITION)
        sync.sync(_BARRIER_FILE_INIT)
        sync.sync(_BARRIER_FILE_EXPELLED)

    # Now expeller can share the Version update of the file with retriever
    # but not the content, since the file was expelled.
    # Wait until this peer receives the versions about the file/dir
    # TODO (MJ) this is AWFUL that I'm just waiting. Somehow I want a way to
    # wait until ExNotFound is received from the expeller.
    time.sleep(7)
    assertFalse(os.path.exists(file_path()))

    with NetworkPartition():
        sync.sync(_BARRIER_VERSION_RECEIVED)
        sync.sync(_BARRIER_FILE_READMIT)

        # Don't break the local network partition until the sourcer device
        # has entered its own network partition
        sync.sync(_BARRIER_SOURCER_PARTITION)

    # Download the file from the expeller device (since sourcer is in
    # a network partition
    files.wait_file_with_content(file_path(), _FILE_CONTENT)

    # Signal to all devices that the file was received and test is complete!
    sync.sync(_BARRIER_FILE_RECEIVED_BY_RETRIEVER)

def non_participant():
    with NetworkPartition():
        for b in (_BARRIER_RETRIEVER_PARTITION,
            _BARRIER_FILE_INIT,
            _BARRIER_FILE_EXPELLED,
            _BARRIER_VERSION_RECEIVED,
            _BARRIER_FILE_READMIT,
            _BARRIER_SOURCER_PARTITION,
            _BARRIER_FILE_RECEIVED_BY_RETRIEVER):
            sync.sync(b)

    files.wait_file_with_content(file_path(), _FILE_CONTENT)

spec = {'entries': [sourcer, expeller, retriever], 'default': non_participant}
