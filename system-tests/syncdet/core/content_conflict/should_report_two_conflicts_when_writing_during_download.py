"""
Test that a conflict branch is successfully created when downloading a file
and writing to that path locally mid-download.
"""

from common import BaseTest
from syncdet.case.sync import sync
from syncdet.case.assertion import assertFalse
from syncdet.case import actor_id
from lib import files
from lib.network_partition import NetworkPartition

import os
import tempfile

_expected_conflicts = 1

class LargeFileSeeder(BaseTest):
    def __init__(self, file_size_bytes):
        self._file_size_bytes = file_size_bytes

    def run(self):
        sync(0)
        os.makedirs(os.path.dirname(self._test_file_path()))

        # Because this file is large, write and flush to file in chunks.
        # Create and write to a temp file, to avoid OS notifications about
        # the file until it is "complete"
        (fd, temp_path) = tempfile.mkstemp()
        with os.fdopen(fd, 'w') as f:
            prefix = "prefix" + str(actor_id())
            f.write(prefix)

            remaining = self._file_size_bytes - len(prefix)
            chunk_size_bytes = 16 * 1024
            while remaining > chunk_size_bytes:
                f.write("0" * chunk_size_bytes)
                f.flush()
                remaining -= chunk_size_bytes

            if remaining > 0:
                f.write("0" * remaining)

        # Move the written temp file into the AeroFS repo
        os.rename(temp_path, self._test_file_path())

        sync(1)
        self._wait_for_n_conflicts(barrier=2, branch_count=_expected_conflicts)


class MidDownloadFileWriter(BaseTest):

    def __init__(self, file_content):
        self._file_content = file_content

    def run(self):
        sync(0)

        # Wait until AeroFS knows about the path (i.e. meta data downloaded)
        # and the folder has been created
        self._r().wait_path(self._test_file_path())
        files.wait_dir(os.path.dirname(self._test_file_path()))

        # This test only works if the download takes sufficiently long for
        # us to locally write first
        assertFalse(os.path.exists(self._test_file_path()))

        with open(self._test_file_path(), 'w') as f:
            f.write(self._file_content)

        # Signal that the conflict file has been created
        sync(1)
        self._wait_for_n_conflicts(barrier=2, branch_count=_expected_conflicts)

class Receiver(BaseTest):
    def run(self):
        sync(0)
        sync(1)

        self._wait_for_n_conflicts(barrier=2, branch_count=_expected_conflicts)

# Seed with a large file to ensure the download is interrupted by a local write
seeder = LargeFileSeeder(30 * 1024 * 1024)
mid_download_writer = MidDownloadFileWriter("small content")

spec = {
    'entries': [seeder.run, mid_download_writer.run],
    'default': Receiver().run
}
