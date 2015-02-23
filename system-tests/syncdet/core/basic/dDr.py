import os
import syncdet.case
from syncdet.case import sync
from lib.files import instance_unique_path, wait_dir, wait_path_to_disappear

# TODO: this file and dFr.py have a symmetric structure. Replace with a template algorithm
def entry():
    luck = syncdet.case.instance_unique_hash32() % syncdet.case.actor_count()
    if luck == syncdet.case.actor_id():
        print 'put', instance_unique_path()
        os.mkdir(instance_unique_path())

        sync.sync('barrier')

        os.rmdir(instance_unique_path())

    else:
        print 'get', instance_unique_path()
        wait_dir(instance_unique_path())

        sync.sync('barrier')

        wait_path_to_disappear(instance_unique_path())

spec = { 'default': entry, 'timeout':20 }
