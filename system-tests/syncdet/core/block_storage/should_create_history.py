from syncdet.case import sync
from syncdet.case.assertion import assertEqual

from lib import ritual
from lib.files import instance_unique_path
from common import *


LAST_VERSION = 5

def version_content(i):
    return str(99-i) + ' bottles of beer on the wall'

def expected_versions():
    return [version_content(i) for i in range(LAST_VERSION)]

def put():
    print 'put', instance_unique_path()

    for i in range(LAST_VERSION + 1):
        ritual_write_file(instance_unique_path(), version_content(i))
        # need to sync to make sure listeners do not miss a version
        sync.sync(i)

    actual = ritual_fetch_all_versions(instance_unique_path())
    assertEqual(actual, expected_versions())

def get():
    print 'get', instance_unique_path()

    for i in range(LAST_VERSION + 1):
        # need to sync to make sure listeners do not miss a version
        ritual_wait_file_with_content(instance_unique_path(), version_content(i))
        sync.sync(i)

    actual = ritual_fetch_all_versions(instance_unique_path())
    assertEqual(actual, expected_versions())

spec = { 'entries': [put], 'default': get }
