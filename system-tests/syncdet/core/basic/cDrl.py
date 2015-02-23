import os
import syncdet.case
from lib.files import instance_unique_path, wait_dir

_DEPTH = 10

def entry():
    luck = syncdet.case.instance_unique_hash32() % syncdet.case.actor_count()
    dirPath = instance_unique_path() + str(luck)
    dirTree = os.path.join(dirPath, *map(str, xrange(_DEPTH)))

    if luck == syncdet.case.actor_id():
        print 'put', dirPath
        os.makedirs(dirTree)
    else:
        print 'get', dirPath
        wait_dir(dirTree)

spec = { 'default': entry, 'timeout':15 }
