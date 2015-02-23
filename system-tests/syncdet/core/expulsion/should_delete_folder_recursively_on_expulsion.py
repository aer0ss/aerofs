import os
from syncdet.case.assertion import assertFalse
import common

def main():
    r = common.create_objects()
    parent = common.parent_path()

    r.exclude_folder(parent)

    assertFalse(os.path.exists(parent))

spec = { 'entries': [main] }