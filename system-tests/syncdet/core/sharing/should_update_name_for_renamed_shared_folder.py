import os
import binascii
import time
from syncdet.case.assertion import assertEqual
from syncdet.case.assertion import fail
from aerofs_sp import sp as sp_service
from syncdet.actors import actor_list
from lib import ritual
from syncdet import case
from syncdet.case.sync import sync
from lib.files import instance_unique_path
from common_multiuser import share_admitted_internal_dir, wait_for_admitted_shared_folder

def creator():
    dir_path_owner = instance_unique_path() + "-OWNER"
    share_admitted_internal_dir(dir_path_owner, ritual.OWNER)
    sync("FOLDER SHARED AS OWNER")
    r = ritual.connect()
    sid = binascii.hexlify(r.get_sid(dir_path_owner))
    sync("FOLDER HAS BEEN RENAMED")
    sync("RENAME CONFIRMED")
    # ensure that name has not changed for creator
    check_folder_name(sid, dir_path_owner, 1)


def renamer():
    dir_path_owner = instance_unique_path() + "-OWNER"
    sync("FOLDER SHARED AS OWNER")
    wait_for_admitted_shared_folder(dir_path_owner)
    r = ritual.connect()
    sid = binascii.hexlify(r.get_sid(dir_path_owner))
    renamed_path = dir_path_owner + "-RENAMED-" + str(case.actor_id())
    os.rename(dir_path_owner, renamed_path)
    sync("FOLDER HAS BEEN RENAMED")
    # poll SP for 60 seconds to see if folder has been renamed
    check_folder_name(sid, renamed_path, 60)
    sync("RENAME CONFIRMED")

def check_folder_name(sid, path, max_count):
    name = os.path.basename(path)
    sp = sp_service.connect()
    sp.sign_in(case.local_actor())
    found=False
    for i in xrange(max_count):
        for folder in sp.list_shared_folders_with_names():
            if folder["sid"] == sid:
                found=True
                print "Expected name: " + name + "; Actual: " + folder["name"]
                if name == folder["name"]:
                    return
                else:
                    break

        time.sleep(1)
    if found:
        fail("Folder found but name does not match. Most likely failed to rename.")
    else:
        fail("Folder " + sid + " not found")


spec = { "entries": [creator], "default": renamer }
