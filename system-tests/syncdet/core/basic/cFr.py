import syncdet.case

from lib.files import instance_unique_path, wait_file_with_content

def entry():
    luck = syncdet.case.instance_unique_hash32() % syncdet.case.actor_count()
    content = 'written by sys {0}'.format(luck)

    path = '{0}.{1}'.format(instance_unique_path(), luck)
    if luck == syncdet.case.actor_id():
        print 'put ' + path
        with open(path, 'wb') as f:
            f.write(content)
    else:
        print 'get', path
        wait_file_with_content(path, content)

spec = { 'default': entry, 'timeout': 8 }
