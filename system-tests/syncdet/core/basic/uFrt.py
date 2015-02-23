import time

from syncdet import case
from lib.files import instance_unique_path, wait_file_with_content

MAX_UPDATES = 300

def entry():
    luck = case.instance_unique_hash32() % case.actor_count()
    updates = 1 + (case.instance_unique_hash32() % MAX_UPDATES)
    if luck == case.actor_id():
        print 'put', instance_unique_path(), updates, 'updates'
        for i in xrange(updates):
            str = 'written by %d update %d' % (luck, i)
            file = open(instance_unique_path(), 'wb')
            file.write(str)
            file.close()
            time.sleep(0.1)
    else:
        print 'get', instance_unique_path()
        str = 'written by %d update %d' % (luck, updates - 1)
        # TODO: add sync and mtime check if this test is ever reinstated...
        wait_file_with_content(instance_unique_path(), str)

spec = {'default': entry}