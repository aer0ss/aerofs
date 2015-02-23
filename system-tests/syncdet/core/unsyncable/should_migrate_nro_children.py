"""
Check that "unsyncable" files and folders can be migrated correctly
"""

import os
import shutil
from syncdet.case.assertion import assertTrue, assertFalse
from lib.files import instance_unique_path, wait_dir
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any
from . import mkspec, instance_path
from lib import ritual


def observe_folders_virtual():
    r = ritual.connect()
    r.wait_shared(instance_path('shared'))
    print 'shared'
    r.wait_path_to_disappear(instance_path('wrap'))
    r.wait_file_with_content(instance_path('shared', 'wrap', 'foo|bar', 'world'), 'hello')
    r.wait_file_with_content(instance_path('shared', 'wrap', 'foo|bar', 'baz:qux', 'hello'), 'world')
    print 'observed'


def creator():
    os.makedirs(instance_unique_path())
    r = ritual.connect()
    r.wait_path(instance_unique_path())
    r.create_object(instance_path('wrap'), True)
    r.create_object(instance_path('wrap', 'foo|bar'), True)
    r.write_file(instance_path('wrap', 'foo|bar', 'world'), 'hello')
    r.create_object(instance_path('wrap', 'foo|bar', 'baz:qux'), True)
    r.write_file(instance_path('wrap', 'foo|bar', 'baz:qux', 'hello'), 'world')

    r.create_object(instance_path('shared'), True)
    r.share_folder(instance_path('shared'))
    shutil.move(instance_path('wrap'), instance_path('shared', 'wrap'))


def observer_linux():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({
        'shared': {
            'wrap': {
                'foo|bar': {
                    'baz:qux': {
                        'hello': 'world'
                    },
                    'world': 'hello'
                }
            },
            '.aerofs': ''
        }
    }, ignore_content=['.aerofs']))


def observer_win():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({
        'shared': {
            'wrap': {
            },
            '.aerofs': '',
            'desktop.ini': ''
        }
    }, ignore_content=['.aerofs', 'desktop.ini']))

    nros = ritual.connect().list_non_representable_objects()

    assertFalse(instance_path('wrap', 'foo|bar') in nros)
    assertFalse(instance_path('wrap', 'foo|bar', 'baz:qux') in nros)
    assertTrue(instance_path('shared', 'wrap', 'foo|bar') in nros)
    assertTrue(instance_path('shared', 'wrap', 'foo|bar', 'baz:qux') in nros)


def observer_osx():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({
        'shared': {
            'wrap': {
                'foo|bar': {
                    'baz:qux': {
                        'hello': 'world'
                    },
                    'world': 'hello'
                }
            },
            '.aerofs': '',
            'Icon\r': ''
        }
    }, ignore_content=['.aerofs', 'Icon\r']))


spec = mkspec(creator, {
    'linux': observer_linux,
    'win': observer_win,
    'osx': observer_osx
})
