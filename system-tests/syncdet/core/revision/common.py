import os
from syncdet.case import sync, actor_id, actor_count, instance_unique_hash32
from lib.files import wait_file_with_content, wait_path_to_disappear

class RevisionTest(object):
    """
    This is the superclass for all Revision History tests. At the moment, it only contains
    methods without side effects. Sharing stateful functions between the two classes added to
    the complexity of the code without much benefit, so (PH) limited this to pure methods.
    """
    def _get_file_stats(self, path):
        if os.path.exists(path):
            return os.stat(path)

    def _sync_version(self, version):
        sync.sync(version)
        return version + 1

    def _fetch_version(self, path, index):
        """
        export a revision to a temporary file and read the first line
        @param r a ritual client
        @param path file to fetch
        @param index revision index (from listRevHistory)
        @return first line of file or an empty string on open failure
        """
        try:
            f = open(self._ritual.export_revision(path, index), 'rb')
        except Exception:
            return ""
        else:
            with f:
                return f.readline()

    @classmethod
    def isLucky(self):
        luck = instance_unique_hash32() % actor_count()
        return luck == actor_id()
