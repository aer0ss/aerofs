
from lib.files import instance_unique_path
from common import ritual_write_file, ritual_wait_file_with_content

CONTENT = 'written by actor 0'

def put():
    print 'put', instance_unique_path()
    ritual_write_file(instance_unique_path(), CONTENT)

def get():
    print 'get', instance_unique_path()
    ritual_wait_file_with_content(instance_unique_path(), CONTENT)

spec = { 'entries': [put], 'default': get }
