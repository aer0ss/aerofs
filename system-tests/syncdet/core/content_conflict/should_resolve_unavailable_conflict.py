# NB: Phoenix-only

import os
import time

from syncdet.case.sync import sync

from aerofs_common import param
from lib import ritual
from lib.files import instance_path, wait_dir, wait_file_with_content, wait_file_new_content
from lib.network_partition import NetworkPartition


def creator():
    os.makedirs(instance_path("foo"))

    sync(1)
    sync(2)

    with open(instance_path("foo", "bar"), "w") as f:
        f.write("baz")

    sync(3)

    with NetworkPartition():
        sync(4)
        sync(5)

    wait_file_new_content(instance_path("foo", "bar"), "qux")


def modifier():
    wait_dir(instance_path("foo"))
    sync(1)

    p = instance_path("foo", "bar")
    with NetworkPartition():
        sync(2)
        with open(p, "w") as f:
            f.write("qux")
        sync(3)
        sync(4)

    # wait for dummy conflict
    r = ritual.connect()
    while True:
        b = r.get_object_attributes(p).object_attributes.branch
        if len(b) > 1:
            print b[1]
            assert b[1].mtime == 0 and b[1].length == 0 and b[1].contributor is None
            break
        time.sleep(param.POLLING_INTERVAL)

    # delete dummy conflict: local branch becomes latest
    r.delete_conflict(p, 1)

    sync(5)


def spectator():
    sync(1)
    sync(2)

    wait_file_with_content(instance_path("foo", "bar"), "baz")

    sync(3)

    with NetworkPartition():
        sync(4)
        sync(5)

    wait_file_new_content(instance_path("foo", "bar"), "qux")


spec = {'entries': [creator, modifier, spectator], 'default': spectator}