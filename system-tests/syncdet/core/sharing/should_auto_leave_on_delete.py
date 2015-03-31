import os
import shutil
import time

from aerofs_common import param
from lib import ritual
import lib.files


def main():
    folder = os.path.join(lib.files.instance_unique_path(), "leaveOnDelete")
    subfolder = os.path.join(folder, "subfolder")

    # create objects
    os.makedirs(subfolder)

    # share the folder
    r = ritual.connect()
    r.share_folder(folder)

    # remove parent folder
    shutil.rmtree(lib.files.instance_unique_path())

    # wait for leave to be processed
    while folder in set(ritual.connect().list_admitted_or_linked_shared_folders()):
        time.sleep(param.POLLING_INTERVAL)


spec = { 'entries': [main] }
