
import os
from lib.files import instance_unique_path
from common import ritual_mkdir, ritual_write_file, ritual_wait_file_with_content, ritual_wait_path_to_disappear, ritual_mv

CONTENT = 'written by actor 0'

def test_file_src():
    return os.path.join(instance_unique_path(), "foo")

def test_file_dst():
    return os.path.join(instance_unique_path(), "bar")

def create():
    print 'create', instance_unique_path()
    ritual_mkdir(instance_unique_path())
    ritual_write_file(test_file_src(), CONTENT)
    ritual_wait_path_to_disappear(test_file_src())
    ritual_wait_file_with_content(test_file_dst(), CONTENT)

def move():
    print 'move', instance_unique_path()
    ritual_wait_file_with_content(test_file_src(), CONTENT)
    ritual_mv(test_file_src(), test_file_dst())

spec = { 'entries': [create], 'default': move }
