import os
from lib import ritual
import lib.files


def main():
    folder = lib.files.instance_unique_path()

    # create objects
    os.makedirs(folder)

    # share the folder
    r = ritual.connect()
    r.share_folder(folder)

    # get SID
    sid = r.get_sid(folder)

    # leave the folder
    r.leave_shared_folder(folder)
    lib.files.wait_path_to_disappear(folder)

    # check we can rejoin
    r.join_shared_folder(sid)
    lib.files.wait_dir(folder)


spec = {'entries': [main]}
