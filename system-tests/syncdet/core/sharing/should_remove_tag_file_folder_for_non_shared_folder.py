"""
When encountering a tag file/folder in a non-shared folder, AeroFS is expected
to delete that tag.
"""

from lib import ritual
from lib.files import instance_unique_path
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any
from syncdet.case import instance_unique_string
from common import TAG_FILE_NAME

def main():

    dt_before = InstanceUniqueDirTree({ TAG_FILE_NAME : { 'subdir' : {} } })

    # write the directory while the linker is paused so that .aerofs is sure to
    # exist when subdir is written to disk. When the linker is resumed, it will
    # force a scan which should notice and remove .aerofs
    r = ritual.connect()
    r.test_pause_linker()
    dt_before.write()
    r.test_resume_linker()

    dt_after = InstanceUniqueDirTree({})
    wait_for_any(dt_after)

spec = { 'entries':[main] }
