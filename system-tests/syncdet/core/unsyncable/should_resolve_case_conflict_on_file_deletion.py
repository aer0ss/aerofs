"""
Check that a new winner is picked when the current winner of a case conflict is deleted
"""

import os
from . import mkspec, files_creator, observe_files_virtual, observe_files_physical, observe_any_files_physical
from lib.files import instance_unique_path
from lib.network_partition import NetworkPartition


CONFLICT_NAMES = ['foo', 'FOO']


def observer_case_sensitive():
    observe_files_virtual(CONFLICT_NAMES)
    observe_files_physical(CONFLICT_NAMES)


def observer_case_insensitive():
    observe_files_virtual(CONFLICT_NAMES)

    with NetworkPartition():
        observe_any_files_physical(CONFLICT_NAMES)

        phy = os.listdir(instance_unique_path())[0]
        print('removing {}'.format(phy))
        os.remove(os.path.join(instance_unique_path(), phy))

        final = [name for name in CONFLICT_NAMES if name != phy]
        print('expect {}'.format(final))
        observe_files_physical(final)


spec = mkspec(files_creator(CONFLICT_NAMES), {
    'linux': observer_case_sensitive,
    'win': observer_case_insensitive,
    'osx': observer_case_insensitive
})
