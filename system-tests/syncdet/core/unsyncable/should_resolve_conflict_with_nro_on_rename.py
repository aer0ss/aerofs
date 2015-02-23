"""
Check that the daemon correctly handles renaming an object to the name of one the CNROs previously shadowed by the
renamed object

NB: this test REQUIRES at least one actor with case-insensitive file system or it will
timeout. That means the low-cost "2 Linux VMs" setup is not sufficient to run it. This
is fine for CI which always include a Windows VM but may surprise unprepared devs.
"""

import os
from lib.app import aerofs_proc
from . import mkspec, instance_path, files_creator, observe_files_virtual, observe_any_files_physical
from syncdet.case.sync import sync
from lib.files.dirtree import wait_for_any, InstanceUniqueDirTree


CONFLICT_NAMES = ['foo', 'FOO']


def observer_case_sensitive():
    observe_files_virtual(CONFLICT_NAMES)

    aerofs_proc.stop_all()
    sync(1)
    aerofs_proc.run_ui()

    wait_for_any(InstanceUniqueDirTree({
        CONFLICT_NAMES[1]: CONFLICT_NAMES[0],
        CONFLICT_NAMES[1] + " (2)": CONFLICT_NAMES[1]
    }))


def observer_case_insensitive():
    observe_files_virtual(CONFLICT_NAMES)

    aerofs_proc.stop_all()

    observe_any_files_physical(CONFLICT_NAMES)

    phy = CONFLICT_NAMES[0]
    nro = CONFLICT_NAMES[1]

    os.rename(instance_path(phy), instance_path(nro))

    aerofs_proc.run_ui()

    wait_for_any(InstanceUniqueDirTree({
        nro: phy,
        nro + " (2)": nro
    }))

    sync(1)


spec = mkspec(files_creator(CONFLICT_NAMES), {
    'linux': observer_case_sensitive,
    'win': observer_case_insensitive,
    'osx': observer_case_insensitive
})
