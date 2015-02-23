"""
Checks that a shared folder created on one device gets propagated to the same user's other devices.

"""
import os
import time

from syncdet import case
from syncdet.case import sync

from aerofs_common import param
from aerofs_common.exception import ExceptionReply
from lib import ritual
from aerofs_ritual.gen.common_pb2 import PBException
from lib.files import instance_unique_path, wait_dir


FOLDER_SYNCED = "welcome my son"
FOLDER_SHARED = "welcome to the machine"

def share():
    print "sharing"

    # Create the folder to be shared
    shared_folder = instance_unique_path()
    os.makedirs(shared_folder)

    # Wait for the folder to appear on the other devices
    sync.sync(FOLDER_SYNCED)

    # Share the folder
    r = ritual.connect()
    r.share_folder(shared_folder)
    sync.sync(FOLDER_SHARED)

def observe():
    # Wait for the folder to be synced
    wait_dir(instance_unique_path())
    sync.sync(FOLDER_SYNCED)

    # Wait for the folder to be shared
    sync.sync(FOLDER_SHARED)

def await():
    print "awaiting"

    # First observe that the folder is synced
    observe()

    # Attempt to set an ACL on the shared folder. If the ACLs were
    # propagated, this should succeed
    r = ritual.connect()

    while True:
        try:
            # Just set the ACL to what it should be by default. This will fail if the
            # folder is not shared
            r.update_acl(instance_unique_path(), case.local_actor().aero_userid, ritual.OWNER)
            break
        except ExceptionReply as e:
            if e.get_type() == PBException.NOT_SHARED:
                # This may be because ACLs are being updated, so wait and
                # try again. The test will fail if this times-out
                time.sleep(param.POLLING_INTERVAL)
            else:
                raise e

# Use the unique hash to randomize which actor shares, instead of always using the
# first defined actor
def run():
    # Sharer and awaiter will never be the same since at least two clients must be present
    # for the test to run
    sharer = case.instance_unique_hash32() % case.actor_count()
    awaiter = (sharer + 1) % case.actor_count()

    if case.actor_id() == sharer:
        share()
    elif case.actor_id() == awaiter:
        await()
    else:
        observe()

# Specifying 2 entries ensures we have at least two clients running
spec = { 'entries': [run] * 2, 'default': run }
