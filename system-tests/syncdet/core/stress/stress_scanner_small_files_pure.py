"""
Exercise scan of a large directory hierarchy with many small files

Use pure scan: i.e files are created when the daemon is not running
"""

import time
from lib import ritual
from lib.app import aerofs_proc
from lib.files import instance_unique_path, make_dir_tree, wait_dir_tree_scanned
import common

# NB: too large hierarchies are quite taxing on VMs due to I/O constraints
DIR_TREE_DEPTH = 4
DIR_TREE_WIDTH = 5
FILES_PER_DIR = 10
FILE_SIZE = 16


def create(pure_scan):
    if pure_scan:
        aerofs_proc.stop_all()

    print 'creating files...'
    create_start = time.time()
    make_dir_tree(instance_unique_path(), DIR_TREE_DEPTH, DIR_TREE_WIDTH,
                  FILES_PER_DIR, FILE_SIZE)
    scan_start = time.time()

    print 'create: {0}s'.format(scan_start - create_start)
    if pure_scan:
        aerofs_proc.run_ui()

    wait_dir_tree_scanned(instance_unique_path(), DIR_TREE_DEPTH, DIR_TREE_WIDTH,
                          FILES_PER_DIR, ritual.connect())

spec = common.stress_spec(entries=[(lambda: create(True))])
