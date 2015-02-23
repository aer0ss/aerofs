"""
Check that case-conflicting files and folders alias correctly

NB: the specific error condition this test is probing only happens
if the aliasing algorithm picks the local object as target on Windows
"""

import os
from .. import wait_aliased
from syncdet.case.assertion import assertFalse, assertTrue
from lib.files import instance_unique_path, wait_dir
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any
from . import mkspec, instance_path
from lib.network_partition import GlobalNetworkPartition
from lib import ritual
from syncdet.case.sync import sync


def observe_folders_virtual():
    wait_dir(instance_unique_path())
    sync('dir')
    r = ritual.connect()
    r.wait_path(instance_unique_path())

    with GlobalNetworkPartition():
        r.write_file(instance_path('foo'), 'hello world')
        r.write_file(instance_path('FOO'), 'baz qux')
        r.create_object(instance_path('bar'), True)
        r.write_file(instance_path('bar', 'baz'), 'hello')
        r.create_object(instance_path('BAR'), True)
        r.write_file(instance_path('BAR', 'BAZ'), 'world')

    # wait for complete aliasing
    wait_aliased("alias-foo", r, instance_path('foo'))
    wait_aliased("alias-FOO", r, instance_path('FOO'))
    wait_aliased("alias-bar/baz", r, instance_path('bar', 'baz'))
    wait_aliased("alias-BAR/BAZ", r, instance_path('BAR', 'BAZ'))

    # ensure content reachable
    r.wait_file_with_content(instance_path('foo'), 'hello world')
    r.wait_file_with_content(instance_path('FOO'), 'baz qux')
    r.wait_file_with_content(instance_path('bar', 'baz'), 'hello')
    r.wait_file_with_content(instance_path('BAR', 'BAZ'), 'world')


def creator():
    os.makedirs(instance_unique_path())


def observer_case_sensitive():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({
        'foo': 'hello world',
        'FOO': 'baz qux',
        'bar': {
            'baz': 'hello'
        },
        'BAR': {
            'BAZ': 'world'
        }
    }))


def observer_case_insensitive():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({
        'foo': 'hello world',
        'bar': {
            'baz': 'hello'
        },
    }))

    # TODO: check SOID?
    nros = ritual.connect().list_non_representable_objects()
    assertTrue(instance_path('FOO') in nros)
    assertTrue(instance_path('BAR') in nros)


spec = mkspec(creator, {
    'linux': observer_case_sensitive,
    'win': observer_case_insensitive,
    'osx': observer_case_insensitive
})
