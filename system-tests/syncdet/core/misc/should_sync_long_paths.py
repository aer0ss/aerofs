import os
from lib.files import instance_unique_path, wait_dir
"""
On Windows, MAX_PATH can only deal with paths that are < 260 characters
long.  We should be able to sync paths longer than that, even if other
applications on Win32 are poorly written and can't handle them.

This test is most useful when run with at least one Windows system.
"""

def get_long_path():
    return os.path.join(instance_unique_path(), *["0123456789"] * 26)

def put():
    os.makedirs(get_long_path())

def get():
    # On windows we must add the magic prefix for file paths > 260 chars
    long_file_path = u"\\\\?\\" + get_long_path()
    wait_dir(long_file_path)

spec = { "entries": [put], "default": get }
