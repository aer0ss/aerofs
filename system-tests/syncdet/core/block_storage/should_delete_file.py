
from lib.files import instance_unique_path
from syncdet.case.assertion import assertEqual
from common import *

CONTENT = 'written by actor 0'

def create():
    print 'create', instance_unique_path()
    ritual_write_file(instance_unique_path(), CONTENT)
    ritual_wait_path_to_disappear(instance_unique_path())

    versions = ritual_fetch_all_versions(instance_unique_path())
    assertEqual([CONTENT], versions)

def delete():
    print 'delete', instance_unique_path()
    ritual_wait_file_with_content(instance_unique_path(), CONTENT)
    ritual_rm(instance_unique_path())

    versions = ritual_fetch_all_versions(instance_unique_path())
    assertEqual([CONTENT], versions)

spec = { 'entries': [create], 'default': delete }
