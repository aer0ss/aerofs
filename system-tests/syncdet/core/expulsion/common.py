import os

from lib import files, ritual


_FILE_CONTENT = "test expulsion should succeed"

def parent_path():
    return os.path.join(files.instance_unique_path(), "parent")

def subfolder_path():
    return os.path.join(parent_path(), 'foo', 'bar', 'folder')

def shared_subfolder_path():
    return os.path.join(parent_path(), 'foo', 'baz', 'shared')

def subfile_path():
    return os.path.join(parent_path(), 'foo', 'qux', 'file')

def file_content():
    return _FILE_CONTENT

def create_objects():
    """
    Create a folder, a file, and a shared folder under a multi-level directory
    structure
    @return ritual client
    """
    subfolder = subfolder_path()
    subfile = subfile_path()
    shared_subfolder = shared_subfolder_path()

    # create physical folders and files
    map(os.makedirs, [subfolder, shared_subfolder, os.path.dirname(subfile)])

    with open(subfile, 'w') as f: f.write(_FILE_CONTENT)

    # wait until objects are accepted by the daemon
    r = ritual.connect()
    r.wait_path(subfolder)
    r.wait_path(shared_subfolder)
    r.wait_path(subfile)

    r.share_folder(shared_subfolder)

    return r
