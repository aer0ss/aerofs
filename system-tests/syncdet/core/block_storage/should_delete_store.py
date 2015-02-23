import os

from syncdet.case.assertion import assertEqual
from syncdet.case.sync import sync

from common import *
from lib import ritual
from lib.files import instance_unique_path


CONTENT = 'written by actor 0'

def test_dir():
    return os.path.join(instance_unique_path(), 'shareme')

def test_file():
    return os.path.join(test_dir(), 'foo')

def put():
    print 'put', test_file()
    ritual_mkdir(instance_unique_path())
    ritual_mkdir(test_dir())
    ritual.connect().share_folder(test_dir())

    sync(0)

    ritual_write_file(test_file(), CONTENT)

    sync(1)

    ritual_rm(test_dir())

    versions = ritual_fetch_all_versions(test_file())
    assertEqual([CONTENT], versions)


def get():
    print 'get', test_file()

    sync(0)

    ritual_wait_shared(test_dir())

    ritual_wait_file_with_content(test_file(), CONTENT)

    sync(1)

    ritual_wait_path_to_disappear(test_dir())

    versions = ritual_fetch_all_versions(test_file())
    assertEqual([CONTENT], versions)


spec = { 'entries': [put], 'default': get }
