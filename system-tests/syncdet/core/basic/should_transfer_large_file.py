"""This test creates a large file and verifies that it can be synced"""
from lib.files import instance_unique_path, wait_file_with_content

content = 'written by sys 0'
FILE_SIZE = 4 * 1024 * 1024 # 4 MB (for faster iteration)

def large_content():
    return (" " * FILE_SIZE) + content

def put():
    print 'put {0}'.format(instance_unique_path())
    with open(instance_unique_path(), 'wb') as f:
        f.write(large_content())

def get():
    print 'get {0}'.format(instance_unique_path())
    wait_file_with_content(instance_unique_path(), large_content())


spec = { 'entries': [put], 'default': get }
