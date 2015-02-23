"""
Windows and Mac are case-insensitive with regard to file names,
so renaming a file from 'f' to 'F' becomes a special case. We test here that
the rename of a file, differing only in case, will be propagated to all other
peers, despite the fact that these OS's don't view the action as a true rename

"""
import os
import shutil
import time

from syncdet.case import sync
from syncdet.case.assertion import assertFalse

from aerofs_common import param
from lib import files


dirname = 'dir'
filename = 'f'
content = 'written by sys 0'
_FILES_RECEIVED = 0

def lower_dir_path():
    return os.path.join(files.instance_unique_path(), dirname.lower())

def upper_dir_path():
    return os.path.join(files.instance_unique_path(), dirname.upper())

def renamer():
    os.makedirs(lower_dir_path())
    with open(os.path.join(lower_dir_path(), filename), 'w') as f: f.write(content)

    # Wait until other peers have downloaded the original file
    sync.sync(_FILES_RECEIVED)

    # Rename the directory name to upper case
    # N.B. shutil.move is case-insensitive with regard to directories,
    # so the following must use os.rename
    os.rename(lower_dir_path(), upper_dir_path())

    # Rename the file name to upper case
    lowerpath = os.path.join(upper_dir_path(), filename)
    upperpath = os.path.join(upper_dir_path(), filename.upper())
    shutil.move(lowerpath, upperpath)

def receiver():
    # Wait to receive the original lower-case filename
    files.wait_file_with_content(os.path.join(lower_dir_path(), filename), content)

    sync.sync(_FILES_RECEIVED)

    # Wait to receive the upper-case filename in the upper-case directory
    # if the lower-case path exists
    # N.B. os.listdir is required instead of os.path.exists as the latter
    # is not case-sensitive
    while dirname.upper() not in os.listdir(files.instance_unique_path()):
        time.sleep(param.POLLING_INTERVAL)

    while filename.upper() not in os.listdir(upper_dir_path()):
        time.sleep(param.POLLING_INTERVAL)

    # Assert that the lower case dir/file are not present locally
    # N.B. cannot use os.path.exists for the same reasons stated above
    assertFalse(dirname.lower() in os.listdir(files.instance_unique_path()))
    assertFalse(filename in os.listdir(upper_dir_path()))

spec = {'entries':[renamer], 'default':receiver}
