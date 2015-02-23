"""
On n devices, in a network partition, create identical hierarchies

Wait until AeroFS aliases all files, i.e. each object has n-1 aliases
on each device
"""

import os
import time

from syncdet.case import actor_id, actor_count

from aerofs_common.param import POLLING_INTERVAL
from lib import ritual
from lib.files import instance_unique_path
from lib.network_partition import GlobalNetworkPartition


_FILE_CONTENT = "Corner cases FTW"
_HIER_DEPTH = 3
_HIER_WIDTH = 4
_LEAF_COUNT = 5

def write(fn, content):
    with open(fn, 'w') as f:
        f.write(content)

def check_alias(fn, r, count):
    aliases = r.test_get_alias_object(fn)
    #print fn, aliases
    return len(aliases) == count

def wait_alias(fn, r, count):
    while not check_alias(fn, r, count):
        time.sleep(POLLING_INTERVAL)

def walk_level(base_dir, depth, width, leaf_count, dir_callback, file_callback):
    if depth == 0:
        dir_callback(base_dir)
        for i in range(leaf_count):
            file_callback(os.path.join(base_dir, str(i)))
    else:
        for i in range(width):
            walk_level(os.path.join(base_dir, str(i)), depth - 1, width, leaf_count, dir_callback, file_callback)

def walk_hierarchy(base_dir, dir_callback, file_callback):
    walk_level(base_dir, _HIER_DEPTH, _HIER_WIDTH, _LEAF_COUNT, dir_callback, file_callback)

def create_hierarchy(base_dir):
    walk_hierarchy(base_dir,
        (lambda d: os.makedirs(d)),
        (lambda f: write(f, _FILE_CONTENT)))

def check_hierarchy(base_dir, r, count):
    walk_hierarchy(base_dir,
        (lambda d: wait_alias(d, r, count)),
        (lambda f: wait_alias(f, r, count)))

def main():
    r = ritual.connect()
    with GlobalNetworkPartition(r):
        create_hierarchy(instance_unique_path())
        check_hierarchy(instance_unique_path(), r, 0)

    check_hierarchy(instance_unique_path(), r, actor_count() - 1)

spec = { 'default': main }
