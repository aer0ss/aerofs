"""
Once upon a time, there was a bug in the collector. The bug manifected with the
following sequence of episodes:

1. Device A and B synced happily ever before
2. Device A created a file, foo, which then propagated to device B
3. Device A expelled foo
4. Device B created another object, bar, which then propagated to A
5. Device A readmitted foo

However, because collector's incorrect behavior on readmission, the foo never
got back to A again...
"""

import os
from core.expulsion import common
from syncdet.case import sync
from syncdet.case.assertion import assertFalse
from lib import files

def bar_path():
    return os.path.join(files.instance_unique_path(), "bar")

def A():
    r = common.create_objects()

    # wait for receivers to receive the file
    sync.sync(0)

    # exclude the folder
    parent = common.parent_path()
    subfile = common.subfile_path()
    r.exclude_folder(parent)
    assertFalse(os.path.exists(subfile))

    # notify that the folder has been excluded
    sync.sync(1)

    # wait for bar to arrive
    files.wait_dir(bar_path())

    # include the folder
    r.include_folder(parent)

    # wait for subfile to arrive
    files.wait_file_with_content(subfile, common.file_content())

def B():
    subfile = common.subfile_path()
    files.wait_file_with_content(subfile, common.file_content())

    # notify that the file has been received
    sync.sync(0)

    # wait for folder exclusion on A
    sync.sync(1)

    # create folder bar
    os.makedirs(bar_path())

spec = { 'entries': [A, B] }