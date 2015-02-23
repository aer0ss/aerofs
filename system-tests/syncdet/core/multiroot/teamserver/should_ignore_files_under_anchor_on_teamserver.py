# IMPORTANT - Conditions for running this test:
#   -   The first actor needs to be a team server configured to use linked storage
#   -   The second actor does not have to be the same user as the first, but must
#   be in the same organization

import os
import time
from . import ts_user_instance_unique_path, expect_ritual_exception
from lib.files import instance_unique_path, wait_dir, wait_file_with_content, wait_path_to_disappear
from lib import ritual
from syncdet.case.sync import sync
from aerofs_ritual.gen.common_pb2 import PBException

FILENAME="deus"
CONTENT="ex machina"

def teamserver():
    folder = ts_user_instance_unique_path()
    path = os.path.join(folder, FILENAME)

    wait_file_with_content(path, CONTENT)

    sync("synced")

    sync("shared")
    # disappearing file indicates the folder to anchor conversion was successful
    wait_path_to_disappear(path)

    # avoid race condition upon anchor conversion
    wait_dir(folder)

    ignored = os.path.join(folder, "ignored")
    with open(ignored, "w") as f:
        f.write(CONTENT)

    # this is not great but there's no perfect way to make sure the file is never picked up
    time.sleep(5)

    r = ritual.connect()
    expect_ritual_exception(r.get_object_attributes_no_wait, PBException.NOT_FOUND)(ignored)


def sharer():
    folder = instance_unique_path()
    os.makedirs(folder)
    path = os.path.join(folder, FILENAME)

    with open(path, "w") as f:
        f.write(CONTENT)

    sync("synced")

    ritual.connect().share_folder(folder)
    sync("shared")


spec = {'entries': [teamserver, sharer]}
