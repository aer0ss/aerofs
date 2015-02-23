"""
This test asserts that a file with a modification time before epoch
(00:00:00 UTC on 1 January 1970) is transfered between two devices.
"""

import os
from stat import ST_ATIME, ST_MTIME
from lib import ritual
from lib import files
from syncdet.case.assertion import assertTrue

_FILE_CONTENT = "Istanbul was Constantinople | Now it's Istanbul, not Constantinople"

def get_file_path():
    return os.path.join(files.instance_unique_path(), 'file')

def sender():
    os.makedirs(os.path.dirname(get_file_path()))

    r = ritual.connect()
    r.test_pause_linker()

    path = get_file_path()
    with open(path, 'w') as f: f.write(_FILE_CONTENT)

    st = os.stat(path)
    atime = st[ST_ATIME] # access time
    mtime = -1           # negative modification time to represent a time before epoch

    os.utime(path, (atime,mtime))

    st = os.stat(path)
    assertTrue(st[ST_MTIME] < 0) # make sure modification time is negative before sending
    r.test_resume_linker()

def receiver():
    path = get_file_path()
    files.wait_file_with_content(path, _FILE_CONTENT)

spec = {'entries': [sender], 'default': receiver}
