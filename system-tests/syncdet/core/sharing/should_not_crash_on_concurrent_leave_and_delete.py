"""
devices A and B share folder foo
daemon is stopped on A and foo is deleted locally
user leaves shared folder from device B
daemon back up on A
=> ACL update induces an auto-leave before the filesystem deletion clears the TimeoutDeletionBufer
  => overly strict expectations of filesystem consistency cause a crash-loop
"""

import os
from lib.files import instance_unique_path, wait_path_to_disappear
from lib import ritual
from syncdet.case.sync import sync
from lib.app import aerofs_proc
from lib.app.install import rm_rf

def shared_folder():
    return os.path.join(instance_unique_path(), "shared")

def delete():
    print 'delete'
    os.makedirs(shared_folder())
    ritual.connect().share_folder(shared_folder())

    # wait for conversion to propagate
    sync(1)

    aerofs_proc.stop_all()

    # notify daemon stopped
    sync(2)

    rm_rf(shared_folder())

    # wait for folder to be left
    sync(3)

    aerofs_proc.run_ui()

    # TODO: make sure the daemon does not crash
    ritual.connect().wait_path_to_disappear(shared_folder())

def leave():
    print 'leave'
    print shared_folder()
    # wait for conversion to propagate
    in_sync = False
    while not in_sync:
        for sf in ritual.connect().list_shared_folders():
            if sf == shared_folder():
                in_sync = True
                break
    # notify successful propagation
    sync(1)

    # wait for other daemon to stop
    sync(2)

    ritual.connect().leave_shared_folder(shared_folder())

    wait_path_to_disappear(shared_folder())

    # notify successful leave
    sync(3)


spec = { "entries": [delete, leave] }
