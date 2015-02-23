"""
Stress-test the time-to-sync for large hierarchies of small files coming
from multiple sources
"""

import os
import time
from lib.files import instance_unique_path, make_dir_tree, wait_dir_tree
from syncdet.case import actor_id, actor_count
from lib.network_partition import GlobalNetworkPartition
import common

DIR_TREE_DEPTH = 4
DIR_TREE_WIDTH = 5
FILES_PER_DIR = 10
FILE_SIZE = 16


def main():
    total_start = time.time()
    with GlobalNetworkPartition():
        create_start = time.time()
        make_dir_tree(os.path.join(instance_unique_path(), str(actor_id())),
                      DIR_TREE_DEPTH, DIR_TREE_WIDTH, FILES_PER_DIR, FILE_SIZE,
                      False, True)
        create_end = time.time()
        print 'create files in ', create_end - create_start

        # wait for scan to be over
        common.wait_no_activity()

    sync_start = time.time()
    for actor in range(actor_count()):
        if actor != actor_id():
            wait_dir_tree(os.path.join(instance_unique_path(), str(actor)),
                          DIR_TREE_DEPTH, DIR_TREE_WIDTH, FILES_PER_DIR, FILE_SIZE)
    sync_end = time.time()
    print 'sync files in ', sync_end - sync_start

    print 'total ', sync_end - total_start

spec = common.stress_spec(default=main)
