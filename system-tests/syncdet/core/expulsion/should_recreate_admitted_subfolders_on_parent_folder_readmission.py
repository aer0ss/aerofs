from os.path import exists
import common
from syncdet.case.assertion import assertFalse, assertTrue

def main():
    r = common.create_objects()
    parent = common.parent_path()
    subfolder = common.subfolder_path()
    shared_subfolder = common.shared_subfolder_path()

    r.exclude_folder(parent)
    assertFalse(exists(parent))

    # unlike should_keep_expelled_subfolder_expelled_on_parent_folder_readmission,
    # this test excludes subfolders _after_ excluding parent. this is for better
    # test coverage.
    r.exclude_folder(subfolder)
    assertFalse(exists(subfolder))
    r.exclude_folder(shared_subfolder)
    assertFalse(exists(shared_subfolder))

    # unlike should_keep_expelled_subfolder_expelled_on_parent_folder_readmission,
    # this test includes subfolders _before_ including parent. this is required
    # by the test spec.
    r.include_folder(shared_subfolder)
    assertFalse(exists(shared_subfolder))
    r.include_folder(subfolder)
    assertFalse(exists(subfolder))

    assertFalse(exists(parent))
    r.include_folder(parent)

    assertTrue(exists(subfolder))
    assertTrue(exists(shared_subfolder))

spec = { 'entries': [main] }