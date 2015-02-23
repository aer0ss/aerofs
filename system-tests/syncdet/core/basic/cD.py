import os
from lib.files import instance_unique_path, wait_dir

def put():
    print 'put', instance_unique_path()
    os.mkdir(instance_unique_path())

def get():
    print 'get', instance_unique_path()
    wait_dir(instance_unique_path())

spec = { 'entries': [put], 'default': get, 'timeout': 8 }
