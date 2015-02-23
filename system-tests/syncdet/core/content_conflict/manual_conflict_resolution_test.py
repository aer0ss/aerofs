import os

from syncdet import case
from syncdet.case import sync

import common
from lib import files, ritual


class ManualConflictResolutionTest(common.BaseTest):
    """
    For n actors, creates n content conflicts for a single file and has one peer resolve them all.

    """
    def __init__(self, file_content_seed):
        self._file_content_seed = file_content_seed

    def _create_conflict(self):
        file_content = "{0} {1}".format(self._file_content_seed, case.actor_id())
        self._write_new_content_while_partitioned(content=file_content)

    def resolver(self):
        os.mkdir(os.path.dirname(self._test_file_path()))

        # Write a file
        with open(self._test_file_path(), 'w') as f:
            f.write(self._file_content_seed)

        # Wait for everyone to get the original file
        sync.sync(0)

        # Create a conflict
        self._create_conflict()

        # Wait for everyone else's conflicts to arrive
        self._wait_for_n_conflicts(branch_count=case.actor_count() - 1)

        # Wait for everyone to receive our conflicts
        sync.sync(1)

        # Everyone is at the same state right now. Resolve the conflict
        for i in xrange(1, case.actor_count()):
            self._r().delete_conflict(self._test_file_path(), i)
        sync.sync(2)

    def spectator(self):
        # Wait for the original file
        files.wait_file_with_content(self._test_file_path(), self._file_content_seed)
        sync.sync(0)

        # Create a conflict
        self._create_conflict()

        # Wait for everyone else's conflicts to arrive
        self._wait_for_n_conflicts(branch_count=case.actor_count() - 1)

        # Wait for everyone to receive our conflicts
        sync.sync(1)

        # Wait for the resolver to resolve the conflicts
        sync.sync(2)

        # Wait for the conflict resolutions to be propagated to us
        self._wait_for_no_conflicts()
