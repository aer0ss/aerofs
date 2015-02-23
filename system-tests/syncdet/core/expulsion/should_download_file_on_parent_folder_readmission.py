import os
from core.expulsion import common
from syncdet.case import sync
from syncdet.case.assertion import assertFalse
from lib import files

def sender():
    r = common.create_objects()

    # wait for receivers to receive the file
    sync.sync(0)

    # exclude the folder
    parent = common.parent_path()
    subfile = common.subfile_path()
    r.exclude_folder(parent)
    assertFalse(os.path.exists(subfile))

    # include the folder
    r.include_folder(parent)

    # wait for subfile to arrive
    files.wait_file_with_content(subfile, common.file_content())

def receiver():
    subfile = common.subfile_path()
    files.wait_file_with_content(subfile, common.file_content())

    # notify that the file has been received
    sync.sync(0)

spec = { 'entries': [sender], 'default': receiver }