import os
import time
from common import RevisionTest
import syncdet.case
from lib import ritual
from syncdet.case.assertion import assertEqual
from lib.files import instance_unique_path, wait_path_to_disappear, wait_file_with_content
from syncdet.case import sync

class _RevisionShouldBuildHistoryTest(RevisionTest):
    """
    This test has two roles: a Modifier and an Observer. More than one actor can be an Observer
    (in theory) but only one actor can be a Modifier. The Modifier modifies a file multiple times
    and the Observer is expected to record revisions. The contents of the file are sequential (0 indexed),
    so the content of the first version is "0", the second version is "1", etc.
    """
    def __init__(self):
        self._rev = 0
        self._ritual = ritual.connect()
        self._path = instance_unique_path()
        self._number_of_updates = 2

    def run(self):
        self._setup()
        self._ensure_modification_creates_revision_history()
        self._ensure_deletion_preserves_revision_history()

    def _setup(self):
        self._assert_revision_history_is_correct(0)
        self._modify_file(self._path, str(self._rev))
        self._assert_revision_history_is_correct(0)

    def _assert_revision_history_is_correct(self, update_count):
        history = self._ritual.list_rev_history(self._path)
        expected_count = self._expected_revision_count(update_count)
        assertEqual(expected_count, len(history))
        self._assert_revision_history_has_correct_content(history, update_count)

    def _modify_file(self, path, content):
        base = self._get_file_stats(path)
        self._sync()
        self._create_file_with_content(path, content)
        self._wait_for_file_with_content(path, content, base)

    def _ensure_modification_creates_revision_history(self):
        for k in range(self._number_of_updates):
            time.sleep(1.2)
            self._modify_file(self._path, str(self._rev))
        self._assert_revision_history_is_correct(self._number_of_updates)

    def _assert_revision_history_has_correct_content(self, history, update_count):
        content = self._get_revision_history_content(history)
        expected_content = self._expected_revision_history_content(update_count)
        assertEqual(expected_content, content)

    def _get_revision_history_content(self, history):
        return  [ self._fetch_version(self._path, c.index) for
                    c in sorted(history, key=lambda e: e.mtime)]

    def _ensure_deletion_preserves_revision_history(self):
        self._sync()
        self._remove_path(self._path)
        self._wait_for_path_to_be_removed(self._path)
        self._assert_revision_history_is_correct(self._number_of_updates + 1)

    def _sync(self):
        self._rev = self._sync_version(self._rev)

    def _expected_revision_count(self, update_count):
        raise NotImplementedError

    def _expected_revision_history_content(self, update_count):
        raise NotImplementedError

    def _create_file_with_content(self, path, content):
        raise NotImplementedError

    def _wait_for_file_with_content(self, path, content, base):
        raise NotImplementedError

    def _remove_path(self, path):
        raise NotImplementedError

    def _wait_for_path_to_be_removed(self, path):
        raise NotImplementedError


class _RevisionShouldBuildHistoryTestModifier(_RevisionShouldBuildHistoryTest):
    """
    The Modifier creates the file, modifies the file, and deletes the file. For
    other method calls, we return a noop. It should never have a revision history,
    so the method calls for expected revision history are hardcoded to 0 and [].
    """

    def _create_file_with_content(self, path, content):
        print 'put {0}'.format(content)
        with open(path, 'w') as f:
            f.write(content)

    def _wait_for_file_with_content(self, path, content, base):
        pass

    def _remove_path(self, path):
        os.remove(path)

    def _wait_for_path_to_be_removed(self, path):
        pass

    def _expected_revision_count(self, update_count):
        return 0

    def _expected_revision_history_content(self, update_count):
        return []

class _RevisionShouldBuildHistoryTestObserver(_RevisionShouldBuildHistoryTest):
    """
    The Observer waits for files to be created, it waits for files to be modified, and it
    waits for them to be deleted. Other method calls are a noop. It should keep a revision
    history, and the expected history it keeps is dependent upon the number of times the file
    was modified.
    """
    def _create_file_with_content(self, path, content):
        pass

    def _wait_for_file_with_content(self, path, content, base):
        print 'get {0}'.format(content)
        wait_file_with_content(self._path, content, base)

    def _remove_path(self, path):
        pass

    def _wait_for_path_to_be_removed(self, path):
        wait_path_to_disappear(path)

    def _expected_revision_count(self, update_count):
        return update_count

    def _expected_revision_history_content(self, update_count):
        return [str(x) for x in xrange(update_count) ]

def isLucky():
    actor_id = syncdet.case.actor_id()
    actor_count = syncdet.case.actor_count()
    luck = syncdet.case.instance_unique_hash32() % actor_count
    return luck == actor_id

def main():
    if RevisionTest.isLucky():
        _RevisionShouldBuildHistoryTestModifier().run()
    else:
        _RevisionShouldBuildHistoryTestObserver().run()

spec = { 'default': main }
