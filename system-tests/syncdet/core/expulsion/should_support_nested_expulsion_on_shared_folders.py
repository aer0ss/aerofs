"""
There was a bug that prevent folder /a to be expelled if one of its children
is a shared folder
"""

import os
import common
from syncdet.case.assertion import assertFalse

def main():
    r = common.create_objects()
    parent = common.parent_path()
    shared_subfolder = common.shared_subfolder_path()

    r.exclude_folder(shared_subfolder)
    assertFalse(os.path.exists(shared_subfolder))

    r.exclude_folder(parent)
    assertFalse(os.path.exists(parent))

spec = { 'entries': [main] }