
import os
import shutil
from functools import partial
from syncdet.case import sync
from lib.files import instance_unique_path, wait_file_with_content, wait_dir

"""
    This library and associated test cases are similar to the ones in
    core.anti_entropy.should_move_file; the difference being the actors in
    anti_entropy library also simulate network partition to see how well
    anti-entropy handles the situation.
"""

_FILE_CONTENT = "hello mr. shark"

def _test_dir_up():
    return os.path.join(instance_unique_path(), "foo")

def _test_dir_down():
    return os.path.join(_test_dir_up(), "bar")

def in_down(relative):
    return lambda: os.path.join(_test_dir_down(), relative)

def in_up(relative):
    return lambda: os.path.join(_test_dir_up(), relative)

def _mover(test_file_from, test_file_to):
    print 'mover'

    # setup directory hierarchy
    os.makedirs(_test_dir_down())

    sync.sync(0)

    # create test file
    with open(test_file_from(), 'w') as f: f.write(_FILE_CONTENT)

    sync.sync(1)

    # rename test file
    shutil.move(test_file_from(), test_file_to())

    wait_file_with_content(test_file_to(), _FILE_CONTENT)

def _syncer(test_file_from, test_file_to):
    print 'syncer'

    wait_dir(_test_dir_down())
    sync.sync(0)

    # wait for test file
    wait_file_with_content(test_file_from(), _FILE_CONTENT)

    sync.sync(1)

    # wait for renamed file
    wait_file_with_content(test_file_to(), _FILE_CONTENT)

def test_spec(src, dst):
    return {
        'entries': [partial(_mover, src, dst)],
        'default': partial(_syncer, src, dst)
    }
