import os
import platform
import socket

from lib import files
from lib.app.cfg import get_cfg
from lib.app import aerofs_proc
from lib import ritual

from syncdet.case.assertion import assertFalse, assertEqual, assertNotEqual, expect_exception
from syncdet.case import instance_unique_string


class _ShouldRelocateRootAnchorTest:
    def __init__(self):
        self._define_root_paths()
        self._define_test_file()

    def _define_root_paths(self):
        self._old_root = get_cfg().get_root_anchor()
        self._old_root_parent_path = os.path.dirname(self._old_root)
        self._new_root_parent_path = os.path.join(
            self._old_root_parent_path,
            files.get_rand_dirname("NEWROOT")
        )
        self._new_root = os.path.join(self._new_root_parent_path, os.path.basename(self._old_root))

    def _define_test_file(self):
        self._test_file = instance_unique_string()
        self._test_file_content = 'But as thick as you are, PAY ATTENTION!'
        self._old_file = os.path.join(self._old_root, self._test_file)
        self._new_file = os.path.join(self._new_root, self._test_file)

    def run(self):
        """ Run the relocate root anchor test """
        self._attempt_to_relocate_root_anchor()
        self._restore_root_anchor_location()

    def _attempt_to_relocate_root_anchor(self):
        self._setup()
        self._relocate_root_anchor(self._new_root)
        self._assert_root_relocated()

    def _setup(self):
        with open(self._old_file, 'w') as f:
            f.write(self._test_file_content)
        self._old_mtime = os.path.getmtime(self._old_file)
        os.mkdir(self._new_root_parent_path)

    def _relocate_root_anchor(self, target):
        # Windows: daemon runs as a Win process, not a cygwin process, so convert to NT-path
        native_target = target
        r = ritual.connect()
        expect_exception(r.relocate, socket.error)(native_target)
        aerofs_proc.stop_all()
        aerofs_proc.run_ui()

    def _assert_root_relocated(self):
        """ Assert the root was actually moved """
        self._assert_root_anchor_moved()
        self._assert_old_root_is_deleted()
        self._assert_mtime_is_preserved()
        self._assert_file_contents_are_preserved()

    def _assert_root_anchor_moved(self):
        actual_new_root = get_cfg().get_root_anchor()
        assertEqual(self._new_root, actual_new_root)
        assertNotEqual(self._old_root, actual_new_root)

    def _assert_old_root_is_deleted(self):
        assertFalse(os.path.exists(self._old_root))

    def _assert_mtime_is_preserved(self):
        new_file = os.path.join(self._new_root, self._test_file)
        new_mtime = os.path.getmtime(new_file)
        assertEqual(self._old_mtime, new_mtime)

    def _assert_file_contents_are_preserved(self):
        new_file = os.path.join(self._new_root, self._test_file)
        # file contents are preserved
        with open(new_file, 'r') as f:
            line = f.readline()
            assertEqual(self._test_file_content, line)

    def _restore_root_anchor_location(self):
        self._relocate_root_anchor(self._old_root)
        os.rmdir(self._new_root_parent_path)


def main():
    test = _ShouldRelocateRootAnchorTest()
    test.run()

spec = { 'default': main }
