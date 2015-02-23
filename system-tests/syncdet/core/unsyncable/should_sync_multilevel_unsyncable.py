"""
Check that multiple levels of "unsyncable" folders sync on all OSes
"""

import os
from lib.files import instance_unique_path, wait_dir
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any
from . import mkspec, instance_path
from lib import ritual


def observe_folders_virtual():
    wait_dir(instance_unique_path())
    r = ritual.connect()
    r.wait_file_with_content(instance_path('foo|bar', 'world'), 'hello')
    r.wait_file_with_content(instance_path('foo|bar', 'baz:qux', 'hello'), 'world')


def creator():
    os.makedirs(instance_unique_path())
    r = ritual.connect()
    r.create_object(instance_path('foo|bar'), True)
    r.write_file(instance_path('foo|bar', 'world'), 'hello')
    r.create_object(instance_path('foo|bar', 'baz:qux'), True)
    r.write_file(instance_path('foo|bar', 'baz:qux', 'hello'), 'world')


def observer_unrestricted():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({
        'foo|bar': {
            'baz:qux': {
                'hello': 'world'
            },
            'world': 'hello'
        }
    }))


def observer_win():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({}))


spec = mkspec(creator, {
    'linux': observer_unrestricted,
    'win': observer_win,
    'osx': observer_unrestricted
})
