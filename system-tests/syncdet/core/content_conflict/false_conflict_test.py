import os
import time

from syncdet import case
from syncdet.case import sync

import common
from lib import files, ritual


class FalseConflictTest(common.BaseTest):
    """
    Tests that resolvable conflicts (such as mtime changes) get resolved automatically.
    This is difficult to test in a system-test because if everything works properly we will
    not see any change in the system. It is difficult to distinguish between success and the
    clients doing no work.

    """
    def __init__(self, file_content_seed):
        self._file_content_seed = file_content_seed

    def _wait_until_conflicts_self_resolve(self):
        # We wait here for the clients to synchronize. There is no way
        # to tell with ritual what version of the file the local peer has
        # nor should there be, therefore it is impossible to reliably synchronize
        # the clients. Using a delay is not ideal but it allows us to
        # at least see if a conflict gets created incorrectly.
        # This is better suited as an integration test.
        time.sleep(5)
        self._wait_for_no_conflicts()

    def file_seeder(self):
        os.mkdir(os.path.dirname(self._test_file_path()))

        # Create the initial file
        with open(self._test_file_path(), 'w') as f:
            f.write(self._file_content_seed)

        # Wait for everyone to receive it
        sync.sync(0)

        # Write the same content, which will cause the mtime to change and
        # create a false conflict
        self._write_new_content_while_partitioned(content=self._file_content_seed)

        # Wait for the conflict to resolve itself
        self._wait_until_conflicts_self_resolve()

    def file_modifier(self):
        # Wait for the original file
        files.wait_file_with_content(self._test_file_path(), self._file_content_seed)
        sync.sync(0)

        # Write the same content, which will cause the mtime to change and
        # create a false conflict
        self._write_new_content_while_partitioned(content=self._file_content_seed)

        # Wait for the conflict to resolve itself
        self._wait_until_conflicts_self_resolve()
