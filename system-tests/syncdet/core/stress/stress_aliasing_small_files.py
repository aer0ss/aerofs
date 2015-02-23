"""
Stress-test the aliasing subsystem by creating identical directory trees on all
actors. Exercise aliasing large hierarchies of small files.
"""

import time
from lib.files import instance_unique_path, make_dir_tree, wait_dir_tree
from lib.network_partition import GlobalNetworkPartition
import common

DIR_TREE_DEPTH = 4
DIR_TREE_WIDTH = 5
FILES_PER_DIR = 10
FILE_SIZE = 16

def main():
    with GlobalNetworkPartition():
        create_start = time.time()
        make_dir_tree(instance_unique_path(),
                      DIR_TREE_DEPTH, DIR_TREE_WIDTH, FILES_PER_DIR, FILE_SIZE,
                      False, True)
        create_end = time.time()
        print 'create files in ', create_end - create_start

    sync_start = time.time()

    # TODO: detect end of transfers, not end of syncstat catchup...
    # maybe leverage activity log?
    common.wait_no_activity()

    activity_end = time.time()
    print 'no more activity after {0}s'.format(activity_end - sync_start)

    # TODO: check for evidence of aliasing...
    wait_dir_tree(instance_unique_path(), DIR_TREE_DEPTH, DIR_TREE_WIDTH,
                  FILES_PER_DIR, FILE_SIZE)
    sync_end = time.time()
    print 'sync confirmed in {0}s'.format(sync_end - sync_start)

spec = common.stress_spec(default=main, timeout=900)
