
import os
import time
import stat
from aerofs_common import param
from lib import ritual
from lib.files import instance_path


def main():
    print 'create'
    os.makedirs(instance_path("foo"))

    with open(instance_path("foo", "bar"), "w") as f:
        f.write("baz")

    print 'mark read-only'

    os.chmod(instance_path("foo", "bar"), stat.S_IREAD)
    os.chmod(instance_path("foo"), stat.S_IREAD)

    print 'delete'

    r = ritual.connect()
    r.wait_path(instance_path())
    r.delete_object(instance_path())

    print 'check history'

    while len(r.list_rev_history(instance_path("foo", "bar"))) != 1:
        time.sleep(param.POLLING_INTERVAL)

spec = {'entries': [main]}