from lib import ritual
from syncdet import case
from syncdet.case.sync import sync
from lib.files import instance_unique_path
from common_multiuser import share_admitted_internal_dir, wait_for_admitted_shared_folder

def sharer():
    dir_path_owner = instance_unique_path() + "-OWNER"
    share_admitted_internal_dir(dir_path_owner, ritual.OWNER)
    sync("FOLDER SHARED AS OWNER")

    dir_path_editor = instance_unique_path() + "-EDITOR"
    share_admitted_internal_dir(dir_path_editor, ritual.EDITOR)
    sync("FOLDER SHARED AS EDITOR")

def sharee():
    dir_path_owner = instance_unique_path() + "-OWNER"
    sync("FOLDER SHARED AS OWNER")
    wait_for_admitted_shared_folder(dir_path_owner)

    dir_path_editor = instance_unique_path() + "-EDITOR"
    sync("FOLDER SHARED AS EDITOR")
    wait_for_admitted_shared_folder(dir_path_editor)

def main():
    actor_id = case.actor_id()
    actor_count = case.actor_count()
    luck = case.instance_unique_hash32() % actor_count
    if luck == actor_id:
        sharer()
    else:
        sharee()

spec = { "default": main }
