import os
import time

from aerofs_common import param
from lib import files, ritual
from lib.network_partition import GlobalNetworkPartition
from syncdet.case.sync import sync, sync_ng


class BaseTest(object):
    def _r(self):
        try:
            return self._ritual
        except AttributeError:
            self._ritual = ritual.connect()
            return self._ritual

    def _test_file_path(self):
        return os.path.join(files.instance_unique_path(), "test")

    def _wait_for_n_conflicts(self, barrier, branch_count):
        path = self._test_file_path()
        sync_ng(barrier,
                # expect:
                # - no conflict branch for first daemon submitting change to polaris
                # - exactly one branch for all other daemons
                validator=lambda votes: sum(votes.itervalues()) == branch_count,
                vote=lambda: 1 if len(self._r().get_object_attributes(path).object_attributes.branch) > 1 else None)

    def _wait_for_no_conflicts(self):
        path = self._test_file_path()
        while True:
            conflicts = self._r().list_conflicts()
            if path not in conflicts:
                break
            time.sleep(param.POLLING_INTERVAL)

    def _write_new_content_while_partitioned(self, content):
        with GlobalNetworkPartition(self._r()):
            with open(self._test_file_path(), 'w') as f:
                f.write(content)
