import os
import common
from syncdet.case.assertion import assertFalse, assertTrue

def main():
    r = common.create_objects()
    parent = common.parent_path()
    subfolder = common.subfolder_path()
    shared_subfolder = common.shared_subfolder_path()

    r.exclude_folder(parent)
    assertFalse(os.path.exists(subfolder))

    r.include_folder(parent)
    assertTrue(os.path.exists(subfolder))
    assertTrue(os.path.exists(shared_subfolder))

spec = { 'entries': [main] }