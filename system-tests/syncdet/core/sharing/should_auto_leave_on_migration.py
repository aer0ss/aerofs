import os
import shutil
import time

from syncdet.case.assertion import assertTrue, assertFalse

from aerofs_common import param
from lib import ritual
import lib.files


_TAG_FILE_NAME = ".aerofs"

def folder_name():
    return "leaveOnMigration"

def shared_folder():
    return os.path.join(lib.files.instance_unique_path(), folder_name())

def subfolder():
    return os.path.join(shared_folder(), "subfolder")

def migrated_folder_parent():
    return os.path.join(lib.files.instance_unique_path(), "migration")

def migrated_folder():
    return os.path.join(migrated_folder_parent(), folder_name())

def migrated_subfolder():
    return os.path.join(migrated_folder(), "subfolder")

def migrated_tag_file():
    return os.path.join(migrated_folder(), _TAG_FILE_NAME)

def main():
    # create objects
    os.makedirs(subfolder())
    os.makedirs(migrated_folder_parent())

    # share the folder
    r = ritual.connect()
    r.share_folder(shared_folder())
    r.share_folder(migrated_folder_parent())

    # move anchor to a different store
    shutil.move(shared_folder(), migrated_folder())

    print 'moved'

    lib.files.wait_path_to_disappear(migrated_tag_file())
    print 'tag removed'

    # wait for leave to be processed
    while migrated_folder() in set(ritual.connect().list_admitted_or_linked_shared_folders()):
        time.sleep(param.POLLING_INTERVAL)

    print 'store left'

    # make sure the content was not removed (i.e migration converted back into regular folder)
    assertTrue(os.path.exists(migrated_subfolder()))
    assertFalse(os.path.exists(migrated_tag_file()))


spec = { 'entries': [main] }
