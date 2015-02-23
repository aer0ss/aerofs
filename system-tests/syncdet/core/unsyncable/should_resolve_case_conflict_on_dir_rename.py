"""
Check that a new winner is picked when the current winner of a case conflict is renamed
"""

import os
from . import mkspec, folders_creator, observe_folders_virtual, observe_folders_physical, observe_any_folders_physical
from lib.files import instance_unique_path
from lib.network_partition import NetworkPartition
from lib.files.dirtree import wait_for_any, InstanceUniqueDirTree


CONFLICT_NAMES = ['foo', 'FOO']


def observer_case_sensitive():
    observe_folders_virtual(CONFLICT_NAMES)
    observe_folders_physical(CONFLICT_NAMES)


def observer_case_insensitive():
    observe_folders_virtual(CONFLICT_NAMES)

    with NetworkPartition():
        observe_any_folders_physical(CONFLICT_NAMES)

        phy = os.listdir(instance_unique_path())[0]
        nro = [name for name in CONFLICT_NAMES if name != phy][0]
        print('renaming {}'.format(phy))
        os.rename(os.path.join(instance_unique_path(), phy), os.path.join(instance_unique_path(), 'bar'))

        final = ['bar', nro]
        print('expect {}'.format(final))
        wait_for_any(InstanceUniqueDirTree({
            'bar': {'sub': phy},
            nro: {'sub': nro}
        }))


spec = mkspec(folders_creator(CONFLICT_NAMES), {
    'linux': observer_case_sensitive,
    'win': observer_case_insensitive,
    'osx': observer_case_insensitive
})
