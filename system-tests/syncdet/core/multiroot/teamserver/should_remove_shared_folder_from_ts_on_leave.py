# IMPORTANT - Conditions for running this test:
#   -   The first actor needs to be a team server configured to use linked storage
#   -   The second actor does not have to be the same user as the first, but must
#   be in the same organization

import os
from . import ts_user_instance_unique_path, ts_shared_instance_unique_path
from lib.files import instance_unique_path, wait_dir, wait_file_with_content, wait_path_to_disappear
from lib import ritual
from syncdet.case.sync import sync

FILENAME="deus"
CONTENT="ex machina"

def teamserver():
    folder = ts_user_instance_unique_path()
    path = os.path.join(folder, FILENAME)

    wait_file_with_content(path, CONTENT)

    sync("synced")

    # wait for conversion to be complete
    wait_path_to_disappear(path)
    wait_file_with_content(os.path.join(ts_shared_instance_unique_path(), FILENAME), CONTENT)
    wait_dir(folder)

    sync("shared")

    wait_path_to_disappear(folder)
    print 'anchor disappeared'
    # NB: ideally the folder would disappear but for consistency that's not currently done
    wait_path_to_disappear(os.path.join(ts_shared_instance_unique_path(), FILENAME))
    print 'shared folder empty'


def sharer():
    folder = instance_unique_path()
    os.makedirs(folder)
    path = os.path.join(folder, FILENAME)

    with open(path, "w") as f:
        f.write(CONTENT)

    sync("synced")

    r = ritual.connect()
    r.share_folder(folder)
    sync("shared")

    r.leave_shared_folder(folder)


spec = {'entries': [teamserver, sharer]}
