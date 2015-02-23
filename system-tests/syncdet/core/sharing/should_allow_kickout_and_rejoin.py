from lib import ritual
from syncdet import case
from syncdet.case.sync import sync
from lib.files import instance_unique_path, wait_path_to_disappear
from common_multiuser import share_admitted_internal_dir, kickout_dir, wait_for_admitted_shared_folder

def sharer():
    dir_path = instance_unique_path()
    share_admitted_internal_dir(dir_path, ritual.EDITOR)
    sync("shared")
    sync("joined")
    kickout_dir(dir_path)
    sync("left")
    share_admitted_internal_dir(dir_path, ritual.EDITOR)
    sync("reshared")


def sharee():
    dir_path = instance_unique_path()
    sync("shared")
    wait_for_admitted_shared_folder(dir_path)
    sync("joined")
    wait_path_to_disappear(dir_path)
    sync("left")
    sync("reshared")
    wait_for_admitted_shared_folder(dir_path)


def main():
    actor_id = case.actor_id()
    actor_count = case.actor_count()
    luck = case.instance_unique_hash32() % actor_count
    if luck == actor_id:
        sharer()
    else:
        sharee()

spec = { "default": main }
