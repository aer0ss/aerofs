"""
This test verifies that after excluding a shared folder,
its original files can be downloaded following re-inclusion. We test both
native files (those that were created in the shared folder after it was
shared) and immigrant files (those files that were moved from a different
shared folder, into the one under test).

"""
import os
import shutil

from syncdet.case import sync
from syncdet.case.assertion import assertFalse

from lib import files, ritual


def dir_path():
    return os.path.join(files.instance_unique_path(), 'd')

def shared_dir_path():
    return os.path.join(files.instance_unique_path(), 's')

def native_file_path():
    return os.path.join(shared_dir_path(), 'nf')

def wait_for_files():
    """
    Wait for both the immigrated file and native file,
    assuming both are in the shared directory
    """
    files.wait_file_with_content(native_file_path(), file_content)
    print 'received native file'
    files.wait_file_with_content(os.path.join(shared_dir_path(), immigrant_file), file_content)
    print 'received immigrant file'

_FILES_RECEIVED = 0

immigrant_file = 'if'
file_content = 'written by actor 0'

def re_admitter():
    # Make two directories, one will be shared, the other not
    map(os.makedirs, [dir_path(), shared_dir_path()])

    # Share the directory (turn it into a store)
    r = ritual.connect()
    r.share_folder(shared_dir_path())

    # Create and write to the file to be immigrated, and the file native to
    # the shared folder
    with open(os.path.join(dir_path(), immigrant_file), 'w') as f:
        f.write(file_content)
    with open(native_file_path(), 'w') as f:
        f.write(file_content)

    # Migrate the file-to-be-immigrated into the shared directory
    shutil.move(os.path.join(dir_path(), immigrant_file), shared_dir_path())

    # Barrier wait until files have been received by remote peers
    sync.sync(_FILES_RECEIVED)

    # Exclude, then include the store
    r.exclude_folder(shared_dir_path())
    assertFalse(os.path.exists(shared_dir_path()))
    r.include_folder(shared_dir_path())

    # Wait to receive the files after re-admitting the store
    wait_for_files()


def sharer():
    # Wait for both the immigrated and native files
    wait_for_files()

    # Barrier signal that files have arrived
    sync.sync(_FILES_RECEIVED)

spec = {'entries':[re_admitter], 'default':sharer, 'timeout':15}
