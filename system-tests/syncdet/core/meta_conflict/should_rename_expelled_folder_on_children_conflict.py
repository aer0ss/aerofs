"""
unlike other tests in the same package which test conflicts on the expelled
folders themselves, this test tests conflicts on the children of the expelled
folder (which causes conflits on the expelled folder, too).
"""

import os
from os.path import join
from syncdet.case.assertion import assertFalse
from lib import files
from lib import ritual

def _original_path():
    return join(files.instance_unique_path(), "excluded")

def _child_path(parent):
    return join(parent, 'foo', 'bar', 'baz', 'quz')

def _alternative_path():
    return _original_path() + " (2)"

def sender():
    path = _original_path()
    child_path = _child_path(path)

    r = ritual.connect()

    os.makedirs(child_path)
    r.wait_path(child_path)

    # exclude the 1st folder
    r.exclude_folder(path)
    assertFalse(os.path.exists(path))

    # recreate a subfolder of the same as what has been expelled.
    # folder 'marker' is used to differentiate the original and the new folder.
    os.makedirs(child_path)
    os.makedirs(join(path, 'marker'))

def receiver():
    files.wait_dir(_child_path(_original_path()))
    files.wait_dir(join(_original_path(), 'marker'))

    # the folder that has been expelled should be renamed to alternative_path
    # when the name conflicting folder is created by the sender.
    files.wait_dir(_child_path(_alternative_path()))

spec = { 'entries': [sender], 'default': receiver }