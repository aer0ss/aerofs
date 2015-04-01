"""
Check that "unsyncable" files and folders are deleted when the parent folder is deleted locally
"""

import os
from syncdet.case.assertion import assertFalse
from lib.files import instance_unique_path, wait_dir
from . import mkspec, instance_path
from lib.network_partition import GlobalNetworkPartition
from lib import ritual
from lib.app.install import rm_rf
from syncdet.case.sync import sync


def observer():
    r = ritual.connect()
    r.wait_file_with_content(instance_path('foo:bar'), 'hello world')
    r.wait_file_with_content(instance_path('baz:qux', 'hello'), 'world')

    sync('files')

    with GlobalNetworkPartition():
        rm_rf(instance_unique_path())

        r.wait_path_to_disappear(instance_unique_path())

        nros = ritual.connect().list_non_representable_objects()
        assertFalse(instance_path('foo:bar') in nros)
        assertFalse(instance_path('baz:qux') in nros)


def creator():
    os.makedirs(instance_unique_path())
    r = ritual.connect()
    r.wait_path(instance_unique_path())
    r.write_file(instance_path('foo:bar'), 'hello world')
    r.create_object(instance_path('baz:qux'), True)
    r.write_file(instance_path('baz:qux', 'hello'), 'world')


spec = mkspec(creator, {
    'linux': observer,
    'win': observer,
    'osx': observer
})
