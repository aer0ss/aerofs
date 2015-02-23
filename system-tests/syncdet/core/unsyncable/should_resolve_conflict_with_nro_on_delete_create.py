"""
Check that the daemon correctly handles deletion of an object and subsequent creation
of an object with the exact same name as one the CNROs previously shadowed by the
deleted object

NB: this test REQUIRES at least one actor with case-insensitive file system or it will
timeout. That means the low-cost "2 Linux VMs" setup is not sufficient to run it. This
is fine for CI which always include a Windows VM but may surprise unprepared devs.
"""

import os
from lib.app import aerofs_proc
from . import mkspec, instance_path, files_creator, observe_files_virtual, observe_files_physical
from syncdet.case.sync import sync
from lib.files.dirtree import wait_for_any, InstanceUniqueDirTree
from lib import ritual


CONFLICT_NAMES = ['foo', 'FOO']


def observer_case_sensitive():
    observe_files_virtual(CONFLICT_NAMES)

    aerofs_proc.stop_all()
    sync(1)
    aerofs_proc.run_ui()

    wait_for_any(InstanceUniqueDirTree({
        CONFLICT_NAMES[1]: "conflict",
        CONFLICT_NAMES[1] + " (2)": CONFLICT_NAMES[1]
    }))


def observer_case_insensitive():
    observe_files_virtual(CONFLICT_NAMES)

    aerofs_proc.stop_all()

    observe_files_physical([CONFLICT_NAMES[0]])

    phy = CONFLICT_NAMES[0]
    nro = CONFLICT_NAMES[1]

    os.remove(instance_path(phy))
    conflict = instance_path(nro)
    with open(conflict, 'wb') as f:
        f.write("conflict")

    aerofs_proc.run_ui()

    ritual.connect().wait_path_to_disappear(instance_path(phy))

    wait_for_any(InstanceUniqueDirTree({
        nro: "conflict",
        nro + " (2)": nro
    }))

    sync(1)


spec = mkspec(files_creator(CONFLICT_NAMES), {
    'linux': observer_case_sensitive,
    'win': observer_case_insensitive,
    'osx': observer_case_insensitive
})
