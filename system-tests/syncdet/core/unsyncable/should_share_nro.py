"""
Check that "unsyncable" files and folders can be shared correctly
"""

import os
from lib.files import instance_unique_path, wait_dir, instance_path
from syncdet.case.assertion import assertTrue
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any
from . import mkspec
from lib import ritual


def observe_folders_virtual():
    wait_dir(instance_unique_path())
    r = ritual.connect()
    r.wait_shared(instance_path('foo|bar'))
    r.wait_file_with_content(instance_path('foo|bar', 'world'), 'hello')
    r.wait_file_with_content(instance_path('foo|bar', 'baz:qux', 'hello'), 'world')
    print 'observed'


def creator():
    os.makedirs(instance_unique_path())
    r = ritual.connect()
    r.wait_path(instance_unique_path())
    r.create_object(instance_path('foo|bar'), True)
    r.write_file(instance_path('foo|bar', 'world'), 'hello')
    r.create_object(instance_path('foo|bar', 'baz:qux'), True)
    r.write_file(instance_path('foo|bar', 'baz:qux', 'hello'), 'world')
    print 'created'
    r.share_folder(instance_path('foo|bar'))
    print 'shared'


def observer_linux():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({
        'foo|bar': {
            'baz:qux': {
                'hello': 'world'
            },
            'world': 'hello',
            '.aerofs': ''
        }
    }, ignore_content=['.aerofs']))


def observer_win():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({}))

    nros = ritual.connect().list_non_representable_objects()
    assertTrue(instance_path('foo|bar') in nros)


def observer_osx():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({
        'foo|bar': {
            'baz:qux': {
                'hello': 'world'
            },
            'world': 'hello',
            '.aerofs': '',
            'Icon\r': ''
        }
    }, ignore_content=['.aerofs', 'Icon\r']))


spec = mkspec(creator, {
    'linux': observer_linux,
    'win': observer_win,
    'osx': observer_osx
})
