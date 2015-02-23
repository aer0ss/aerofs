"""
Stress-test the time-to-sync for large hierarchies of small files coming
from a single source
"""

import time
from lib.files import instance_unique_path, make_dir_tree, wait_dir_tree
from lib.network_partition import GlobalNetworkPartition
import common

DIR_TREE_DEPTH = 4
DIR_TREE_WIDTH = 5
FILES_PER_DIR = 10
FILE_SIZE = 16


def creator():
    print 'creator'

    with GlobalNetworkPartition():
        create_start = time.time()
        make_dir_tree(instance_unique_path(),
                      DIR_TREE_DEPTH, DIR_TREE_WIDTH, FILES_PER_DIR, FILE_SIZE,
                      False, True)
        create_end = time.time()
        print 'create files in ', create_end - create_start

        # wait for scan to be over
        common.wait_no_activity()


def syncer():
    print 'syncer'

    with GlobalNetworkPartition():
        pass

    sync_start = time.time()
    wait_dir_tree(instance_unique_path(),
                  DIR_TREE_DEPTH, DIR_TREE_WIDTH, FILES_PER_DIR, FILE_SIZE)
    sync_end = time.time()
    print 'sync files in ', sync_end - sync_start

spec = common.stress_spec(entries=[creator], default=syncer)
