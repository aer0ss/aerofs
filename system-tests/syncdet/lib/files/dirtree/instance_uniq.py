import os
import time
import sys

from syncdet.case.assertion import assertFalse

from aerofs_common import param
from dirtree import DirTree
from .. import files


class InstanceUniqueDirTree:
    """
    A wrapper of DirTree which knows that its root_path is always
    supposed to be files.instance_unique_path. This simplifies the API
    """
    def __init__(self, tree, ignore_content=None):
        self._dict = tree
        self._ignore_content = ignore_content

    def dt(self):
        if not hasattr(self, '_dt'):
            self._dt = DirTree(os.path.basename(files.instance_unique_path()), self._dict,
                               ignore_content=self._ignore_content)
        return self._dt

    def write(self, ignore_existing_dir = False, verbose = False):
        root_path = os.path.dirname(files.instance_unique_path())
        self.dt().write(root_path, ignore_existing_dir, verbose)

    def leaf_nodes(self):
        return self.dt().leaf_nodes()

    def represents_fs(self):
        magic_prefix = "\\\\?\\" if 'win32' in sys.platform else ''
        return self.dt().represents(magic_prefix + files.instance_unique_path())


def wait_for_any(*instance_unique_dts):
    """
    Wait for instance_unique_path() to match any of the given DirTree objects
    """
    wait_for_any_but(None, *instance_unique_dts)


def wait_for_any_but(rejected_dt, *accepted_dts):
    """
    Wait for instance_unique_path() to match any of the given DirTree objects
    and fail fast if it matches the rejected DirTree
    """
    assert accepted_dts

    files.wait_dir(files.instance_unique_path())
    while not any([dt.represents_fs() for dt in accepted_dts]):
        assertFalse(rejected_dt and rejected_dt.represents_fs())
        time.sleep(param.POLLING_INTERVAL)
