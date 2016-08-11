"""
There used to be a bug when creating the tag file for such folders

Check that the content of such folders can be synced correctly.

NB: for this test to be useful, there should be at least one Windows actor.
"""

import os
import sys
from lib import ritual
from lib.app import aerofs_proc
from lib.files import instance_path, wait_dir, wait_file, wait_file_with_content
from syncdet.case import actor_id, actor_count, instance_unique_hash32
from syncdet.case.sync import sync


TRAILING = "foo."


def _is_lucky():
    luck = instance_unique_hash32() % actor_count()
    return luck == actor_id()


def main():
    if 'win32' in sys.platform:
        folder_path = "\\\\?\\" + instance_path(TRAILING)
    else:
        folder_path = instance_path(TRAILING)

    if _is_lucky():
        print "create {}".format(folder_path)
        os.makedirs(folder_path)

    # wait for dir to sync on all devices
    wait_dir(folder_path)
    sync(0)

    if _is_lucky():
        r = ritual.connect()
        r.share_folder(instance_path(TRAILING))

    # wait for tag file
    wait_file(os.path.join(folder_path, '.aerofs'))
    sync('shared')

    # stop client before creating children to ensure scanner is exercised instead of notifier
    aerofs_proc.stop_all()

    # This is creating a child file inside the folder with trailing whitespaces.
    with open(os.path.join(folder_path, str(actor_id())), 'w') as f:
        f.write(str(actor_id()))

    aerofs_proc.run_ui()

    # wait for all child dirs to sync
    for i in range(0, actor_count()):
        wait_file_with_content(os.path.join(folder_path, str(i)), str(i))


spec = {'default': main}
