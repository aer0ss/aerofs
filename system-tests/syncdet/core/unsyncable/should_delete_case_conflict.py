"""
Check that case-conflicted files and folders are deleted when the parent folder is deleted locally
"""

import os
from lib import aero_shutil as shutil
from syncdet.case.assertion import assertFalse
from lib.files import instance_unique_path, wait_dir
from . import mkspec, instance_path
from lib.network_partition import GlobalNetworkPartition
from lib import ritual
from syncdet.case.sync import sync


def observer():
    r = ritual.connect()
    r.wait_file_with_content(instance_path('FOO'), 'hello world')
    r.wait_file_with_content(instance_path('foo', 'bar'), 'hello')
    r.wait_file_with_content(instance_path('foo', 'BAR'), 'world')

    sync('files')

    with GlobalNetworkPartition():
        shutil.rmtree(instance_unique_path())

        r.wait_path_to_disappear(instance_unique_path())

        nros = ritual.connect().list_non_representable_objects()
        assertFalse(instance_path('FOO') in nros)
        assertFalse(instance_path('foo', 'BAR') in nros)


def creator():
    os.makedirs(instance_unique_path())
    r = ritual.connect()
    r.wait_path(instance_unique_path())
    r.create_object(instance_path('foo'), True)
    r.write_file(instance_path('foo', 'bar'), 'hello')
    r.write_file(instance_path('foo', 'BAR'), 'world')
    r.write_file(instance_path('FOO'), 'hello world')


spec = mkspec(creator, {
    'linux': observer,
    'win': observer,
    'osx': observer
})
