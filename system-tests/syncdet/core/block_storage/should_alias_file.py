from syncdet.case import actor_count

from lib import ritual
from lib.files import instance_unique_path
from lib.network_partition import GlobalNetworkPartition


CONTENT = 'hello world'

def main():
    r = ritual.connect()
    with GlobalNetworkPartition():
        print 'put', instance_unique_path()
        r.write_file(instance_unique_path(), CONTENT)

    r.test_wait_aliased(instance_unique_path(), actor_count() - 1)
    r.wait_file_with_content(instance_unique_path(), CONTENT)


spec = {'default': main}
