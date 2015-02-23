"""
Create a large directory hierarchy filled with thousands of files and migrates them to a different store.
"""

import os.path
import shutil
import lib.files
from lib import ritual
from syncdet.case import sync
from syncdet.case.assertion import assertTrue

_FILE_CONTENT = "Corner cases FTW"

def test_share():
    return os.path.join(lib.files.instance_unique_path(), "share")

def test_dir():
    return os.path.join(test_share(), "foo")

def test_dir_migrated():
    return os.path.join(lib.files.instance_unique_path(), "foo-migrated")

def write(fn, content):
    with open(fn, 'w') as f:
        f.write(content)

def walk_level(base_dir, depth, width, leaf_count, dir_callback, file_callback):
    if depth == 0:
        dir_callback(base_dir)
        for i in range(leaf_count):
            file_callback(os.path.join(base_dir, str(i)))
    else:
        for i in range(width):
            walk_level(os.path.join(base_dir, str(i)), depth - 1, width, leaf_count, dir_callback, file_callback)

# (4+1)^(5-1) = 1k dirs * (10+1) files = a shitload of objects
def walk_hierarchy(base_dir, dir_callback, file_callback):
    walk_level(base_dir, 4, 5, 10, dir_callback, file_callback)

def create_hierarchy(base_dir):
    walk_hierarchy(base_dir,
        (lambda d: os.makedirs(d)),
        (lambda f: write(f, _FILE_CONTENT)))

def wait_hierarchy(base_dir):
    walk_hierarchy(base_dir,
        (lambda d: lib.files.wait_dir(d)),
        (lambda f: lib.files.wait_file_with_content(f, _FILE_CONTENT)))

def creator():
    print 'creator'

    # setup directory hierarchy
    os.makedirs(test_share())
    ritual.connect().share_folder(test_share())

    create_hierarchy(test_dir())

    # migrate out
    sync.sync(1)
    shutil.move(test_dir(), test_dir_migrated())

    # wait for successful migration
    ritual.connect().wait_path(test_dir_migrated())


def syncer():
    print 'syncer'

    wait_hierarchy(test_dir())

    sync.sync(1)

    wait_hierarchy(test_dir_migrated())

    # heartbeat check
    ritual.connect().heartbeat()


spec = {'entries': [creator], 'default': syncer, 'timeout': 900}
