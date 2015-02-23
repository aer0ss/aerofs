import os
import syncdet.case
from syncdet.case import sync
from lib.files import instance_unique_path, wait_file_with_content, wait_path_to_disappear

# TODO: this file and dDr.py have a symmetric structure. Replace with a template algorithm
def entry():
    luck = syncdet.case.instance_unique_hash32() % syncdet.case.actor_count()
    content = 'written by sys {0}'.format(luck)

    if luck == syncdet.case.actor_id():
        print 'put', instance_unique_path()
        with open(instance_unique_path(), 'wb') as f:
            f.write(content)

        sync.sync('barrier')

        os.remove(instance_unique_path())

    else:
        print 'get', instance_unique_path()
        wait_file_with_content(instance_unique_path(), content)

        sync.sync('barrier')

        wait_path_to_disappear(instance_unique_path())

spec = { 'default': entry, 'timeout':20 }
