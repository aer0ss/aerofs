"""This test creates a large file and verifies that it can be synced"""
from lib.files import instance_unique_path, wait_file


def put():
    print 'put {0}'.format(instance_unique_path())
    with open(instance_unique_path(), 'wb') as f:
        for i in xrange(0, 4096):
            f.write(' ' * 4095 + '\n')


def get():
    print 'get {0}'.format(instance_unique_path())
    wait_file(instance_unique_path(), size=(4096 * 4096))


spec = { 'entries': [put], 'default': get }
