"""
Test listRevChildren output when history exists for both file and folder
for a given name
"""

import os
from lib import aero_shutil as shutil
import time
from lib import ritual
from common import RevisionTest
from syncdet.case import sync
from syncdet.case.assertion import assertEqual
from lib.files import instance_unique_path, wait_dir, wait_path_to_disappear, wait_file_with_content

class _ShouldAllowFileAndFolderWithSameNameTest(RevisionTest):
    def __init__(self):
        self._path = instance_unique_path()
        self._foo_path = os.path.join(self._path, "foo")
        self._bar_path = os.path.join(self._foo_path, "bar")
        self._ritual = ritual.connect()
        self._rev = 0

    def run(self):
        self._setup()
        self._ensure_history_is_built_when_a_file_is_modified()
        self._ensure_history_is_built_when_a_dir_with_the_same_name_is_created()
        self._ensure_history_is_built_when_a_file_with_the_same_name_is_created()

    def _setup(self):
        self._ensure_history_children_are_correct_for_path(self._path, [])
        self._create_and_wait_for_dir(self._path)
        self._ensure_history_children_are_correct_for_path(self._path, [])

    def _ensure_history_is_built_when_a_file_is_modified(self):
        self._modify_file(self._foo_path, "Look down")
        time.sleep(1.2)
        self._modify_file(self._foo_path, "Don't look them in the eye")
        self._ensure_history_children_are_correct_for_path(self._path, [("foo",0)])

    def _ensure_history_is_built_when_a_dir_with_the_same_name_is_created(self):
        self._remove_and_wait_for_file(self._foo_path)
        self._create_and_wait_for_dir(self._foo_path)
        self._modify_file(self._bar_path, "Look down")
        time.sleep(1.2)
        self._modify_file(self._bar_path, "You're here until you die")
        self._ensure_history_children_are_correct_for_path(self._path, [("foo", 1), ("foo", 0)])
        self._ensure_history_children_are_correct_for_path(self._foo_path, [("bar", 0)])
        self._ensure_history_children_are_correct_for_path(self._bar_path, [])

    def _ensure_history_is_built_when_a_file_with_the_same_name_is_created(self):
        self._remove_and_wait_for_file(self._foo_path)
        self._modify_file(self._foo_path, "24601")
        self._ensure_history_children_are_correct_for_path(self._path, [("foo", 1), ("foo", 0)])
        self._ensure_history_children_are_correct_for_path(self._foo_path, [("bar", 0)])
        self._ensure_history_children_are_correct_for_path(self._bar_path, [])

    def _remove_and_wait_for_file(self, path):
        self._sync()
        self._remove_or_wait_for_path(path)

    def _create_and_wait_for_dir(self, path):
        self._sync()
        self._create_or_wait_for_dir(path)

    def _sync(self):
        sync.sync(self._rev)
        self._rev = self._rev + 1

    def _modify_file(self, path, content):
        base = self._get_file_stats(path)
        self._sync()
        self._create_or_wait_for_file(path, content, base)

    def _create_or_wait_for_file(self, path, content, base):
        raise NotImplementedError

    def _ensure_history_children_are_correct_for_path(self, path, expected):
        raise NotImplementedError

    def _remove_or_wait_for_path(self, path):
        raise NotImplementedError

    def _create_or_wait_for_dir(self, path):
        raise NotImplementedError

class _ShouldAllowFileAndFolderWithSameNameTestModifier(_ShouldAllowFileAndFolderWithSameNameTest):
    """ The Modifier in the test creates files and directories which are sync'd to the Observer """
    def _ensure_history_children_are_correct_for_path(self, path, expected):
        # in this test, we should never have any revision history in the modifier
        actual = frozenset([ (pbc.name, pbc.is_dir) for pbc in self._ritual.list_rev_children(path)])
        assertEqual(actual, frozenset([]))

    def _create_or_wait_for_dir(self, path):
        """ We create the dir """
        os.mkdir(path)

    def _create_or_wait_for_file(self, path, content, base):
        """ We create the file """
        print 'put {0}'.format(content)
        with open(path, 'w') as f:
            f.write(content)

    def _remove_or_wait_for_path(self, path):
        """ We remove the path """
        if os.path.isdir(path):
            shutil.rmtree(path)
        else:
            os.remove(path)

class _ShouldAllowFileAndFolderWithSameNameTestObserver(_ShouldAllowFileAndFolderWithSameNameTest):
    """ The Observer in the test waits for files and contains the Revision History """
    def _ensure_history_children_are_correct_for_path(self, path, expected):
        actual = frozenset([ (pbc.name, pbc.is_dir) for pbc in self._ritual.list_rev_children(path)])
        assertEqual(actual, frozenset(expected))

    def _create_or_wait_for_dir(self, path):
        """ We wait for the dir """
        wait_dir(path)

    def _create_or_wait_for_file(self, path, content, base):
        """ We wait for the file """
        print 'get {0}'.format(content)
        wait_file_with_content(path, content, base)

    def _remove_or_wait_for_path(self, path):
        """ We wait for the path """
        wait_path_to_disappear(path)

def main():
    if RevisionTest.isLucky():
        _ShouldAllowFileAndFolderWithSameNameTestModifier().run()
    else:
        _ShouldAllowFileAndFolderWithSameNameTestObserver().run()

spec = { 'default': main }
