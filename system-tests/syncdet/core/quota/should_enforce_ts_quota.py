"""
- set quota
- create a (quota+1) byte file (first_file.txt) and verify that it syncs
- create a (quota/2) byte file (second_file.txt) parallel to the first, and verify that it doesn't sync
- create a directory (subdir) and verify that it syncs
- move first_file.txt into subdir and verify that it syncs
- delete first_file.txt and verify that it syncs AND that second_file.txt syncs
- create (quota+1) byte file (third_file.txt) and verify that it syncs
- create a (quota+1) byte file (fourth_file.txt) and verify that it doesn't sync
- remove the quota, and verify that fourth_file.txt syncs

"""
import os
import shutil
from time import sleep

from syncdet.case import instance_unique_string
from syncdet.case.sync import sync

from aerofs_sp import sp as sp_service
from core.block_storage.common import ts_user_path
from lib import ritual
from lib.files import instance_unique_path


QUOTA = 100
FILE_1_NAME = "first_file.txt"
FILE_1_CONT = "A" * (QUOTA + 1)
FILE_2_NAME = "second_file.txt"
FILE_2_CONT = "B" * (QUOTA / 2)
FILE_3_NAME = "third_file.txt"
FILE_3_CONT = "C" * (QUOTA + 1)
FILE_4_NAME = "fourth_file.txt"
FILE_4_CONT = "D" * (QUOTA + 1)
SUBDIR_NAME = "subdir"

def teamserver():
    file_1_tspath = ts_user_path(os.path.join(instance_unique_string(), FILE_1_NAME))
    file_2_tspath = ts_user_path(os.path.join(instance_unique_string(), FILE_2_NAME))
    file_3_tspath = ts_user_path(os.path.join(instance_unique_string(), FILE_3_NAME))
    file_4_tspath = ts_user_path(os.path.join(instance_unique_string(), FILE_4_NAME))
    subdir_tspath = ts_user_path(os.path.join(instance_unique_string(), SUBDIR_NAME))
    file_1_moved_tspath = ts_user_path(os.path.join(instance_unique_string(), SUBDIR_NAME, FILE_1_NAME))

    sp = sp_service.connect()
    sp.sign_in()
    sp.set_quota(QUOTA)
    sync("set quota")

    r = ritual.connect()
    r.wait_pbpath_with_content(file_1_tspath, FILE_1_CONT)
    r.test_check_quota()
    sync("file 1 created")

    r.wait_pbpath(subdir_tspath)
    sleep(5)
    assert len(r.get_pbpath_content_branches(file_2_tspath)) == 0
    sync("subdir created")

    r.wait_pbpath_with_content(file_1_moved_tspath, FILE_1_CONT)
    sync("file 1 moved")

    r.wait_pbpath_to_disappear(file_1_moved_tspath)
    r.test_check_quota()
    r.wait_pbpath_with_content(file_2_tspath, FILE_2_CONT)
    sync("file 1 deleted")

    r.wait_pbpath_with_content(file_3_tspath, FILE_3_CONT)
    r.test_check_quota()
    sync("file 3 created")

    sleep(5)
    assert len(r.get_pbpath_content_branches(file_4_tspath)) == 0
    sync("file 4 created")

    sp.remove_quota()
    r.test_check_quota()
    r.wait_pbpath_with_content(file_4_tspath, FILE_4_CONT)


def client():
    file_1_path = os.path.join(instance_unique_path(), FILE_1_NAME)
    file_2_path = os.path.join(instance_unique_path(), FILE_2_NAME)
    file_3_path = os.path.join(instance_unique_path(), FILE_3_NAME)
    file_4_path = os.path.join(instance_unique_path(), FILE_4_NAME)
    subdir_path = os.path.join(instance_unique_path(), SUBDIR_NAME)
    file_1_moved_path = os.path.join(subdir_path, FILE_1_NAME)

    sync("set quota")

    os.makedirs(instance_unique_path())
    with open(os.path.join(instance_unique_path(), FILE_1_NAME), 'w') as f:
        f.write(FILE_1_CONT)
    sync("file 1 created")

    with open(os.path.join(instance_unique_path(), FILE_2_NAME), 'w') as f:
        f.write(FILE_2_CONT)
    os.makedirs(os.path.join(instance_unique_path(), SUBDIR_NAME))
    sync("subdir created")

    shutil.move(file_1_path, file_1_moved_path)
    sync("file 1 moved")

    os.remove(file_1_moved_path)
    sync("file 1 deleted")

    with open(os.path.join(instance_unique_path(), FILE_3_NAME), 'w') as f:
        f.write(FILE_3_CONT)
    sync("file 3 created")

    with open(os.path.join(instance_unique_path(), FILE_4_NAME), 'w') as f:
        f.write(FILE_4_CONT)
    sync("file 4 created")


spec = {'entries': [teamserver], 'default': client}
