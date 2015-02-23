"""
There was a bug that prevent folder /a to be expelled if its grand children
/a/b/c has been expelled
"""

from os.path import exists, dirname
import common
from syncdet.case.assertion import assertFalse

def main():

    r = common.create_objects()
    parent = common.parent_path()
    subfolder = common.subfolder_path()

    r.exclude_folder(subfolder)
    assertFalse(exists(subfolder))

    r.exclude_folder(dirname(subfolder))
    assertFalse(exists(dirname(subfolder)))

    r.exclude_folder(parent)
    assertFalse(exists(parent))

spec = { 'entries': [main] }