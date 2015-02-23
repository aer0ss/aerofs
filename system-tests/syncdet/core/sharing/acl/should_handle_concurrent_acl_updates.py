"""
This test is a stress test of ACLs. By doing multiple rapid includes and excludes of a
share, we trigger concurrent ACL updates. These should be handled properly and the final
state should be correct.
"""
from syncdet.case import sync
from lib import files
from lib import ritual
from os.path import join
import os


class _ShouldHandleConcurrentAclUpdates(object):
    def _setup(self):
        self._path = files.instance_unique_path()
        self._subpaths = [ join(self._path, str(i)) for i in xrange(3) ]
        self._files_in_subpaths = [ join(path, "Juke Box Hero") for path in self._subpaths ]
        self._file_content = "with stars in his eyes"
        self._ritual = ritual.connect()
        self._number_of_acl_updates = 40

    def _wait_for_files(self):
        for path in self._files_in_subpaths:
           files.wait_file_with_content(path, self._file_content)

    def _wait_for_files_to_disappear(self):
        for path in self._subpaths:
            files.wait_path_to_disappear(path)


class _ShouldHandleConcurrentAclUpdatesVerifier(_ShouldHandleConcurrentAclUpdates):
    def run(self):
        self._setup()
        self._create_files()
        self._exclude_paths()
        self._cause_lots_of_acl_updates()
        self._include_paths()
        self._verify_final_state()

    def _create_files(self):
        os.mkdir(self._path)
        self._create_shared_files()
        sync.sync("Files Arrived")

    def _create_shared_files(self):
        for path in self._subpaths:
            os.mkdir(path)
            self._ritual.share_folder(path)
        for file_in_subpath in self._files_in_subpaths:
            with open(file_in_subpath, "w") as f:
                f.write(self._file_content)

    def _include_paths(self):
        for path in self._subpaths:
            self._ritual.include_folder(path)

    def _exclude_paths(self):
        for path in self._subpaths:
            self._ritual.exclude_folder(path)

    def _cause_lots_of_acl_updates(self):
        for x in xrange(self._number_of_acl_updates):
            self._include_paths()
            self._exclude_paths()

    def _verify_final_state(self):
        # our final state should be that the files are included
        self._wait_for_files()


class _ShouldHandleConcurrentAclUpdatesSharer(_ShouldHandleConcurrentAclUpdates):
    def run(self):
        self._setup()
        self._wait_for_files()
        sync.sync("Files Arrived")


spec = { 'entries': [
    _ShouldHandleConcurrentAclUpdatesVerifier().run,
    _ShouldHandleConcurrentAclUpdatesSharer().run
]}
