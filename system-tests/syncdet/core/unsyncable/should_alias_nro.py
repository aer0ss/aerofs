"""
Check that "unsyncable" files and folders alias correctly
"""

import os
from .. import wait_aliased
from syncdet.case.assertion import assertTrue
from lib.files import instance_unique_path, instance_path, wait_dir
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any
from . import mkspec
from lib.network_partition import GlobalNetworkPartition
from lib import ritual
from syncdet.case import actor_count
from syncdet.case.sync import sync


def observe_folders_virtual():
    wait_dir(instance_unique_path())
    sync('dir')
    r = ritual.connect()
    r.wait_path(instance_unique_path())

    with GlobalNetworkPartition():
        r.write_file(instance_path('foo:bar'), 'hello world')
        r.create_object(instance_path('baz:qux'), True)
        r.write_file(instance_path('baz:qux', 'hello'), 'world')

    # wait for complete aliasing
    wait_aliased("alias-foo:bar", r, instance_path('foo:bar'))
    wait_aliased("alias-baz:qux", r, instance_path('baz:qux'))
    wait_aliased("alias-baz:qux/hello", r, instance_path('baz:qux', 'hello'))

    # ensure content reachable
    r.wait_file_with_content(instance_path('foo:bar'), 'hello world')
    r.wait_file_with_content(instance_path('baz:qux', 'hello'), 'world')


def creator():
    os.makedirs(instance_unique_path())


def observer_allow_colon():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({
        'foo:bar': 'hello world',
        'baz:qux': {
            'hello': 'world'
        }
    }))


def observer_disallow_colon():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({
    }))

    # TODO: check SOID?
    nros = ritual.connect().list_non_representable_objects()
    assertTrue(instance_path('foo:bar') in nros)


spec = mkspec(creator, {
    'linux': observer_allow_colon,
    'win': observer_disallow_colon,
    'osx': observer_allow_colon
})
