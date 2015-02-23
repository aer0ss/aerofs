"""
Check that case-conflicted files and folders can be migrated correctly
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
    r.wait_path_to_disappear(instance_path('foo'))
    r.wait_path(instance_path('shared', 'foo', 'BAR'))
    r.wait_file_with_content(instance_path('shared', 'foo', 'bar', 'baz'), 'qux')
    r.wait_file_with_content(instance_path('shared', 'foo', 'bar', 'BAZ'), 'quux')
    print 'observed'


def creator():
    os.makedirs(instance_unique_path())
    r = ritual.connect()
    r.wait_path(instance_unique_path())
    r.create_object(instance_path('foo'), True)
    r.create_object(instance_path('foo', 'bar'), True)
    r.create_object(instance_path('foo', 'BAR'), True)
    r.write_file(instance_path('foo', 'bar', 'baz'), 'qux')
    r.write_file(instance_path('foo', 'bar', 'BAZ'), 'quux')

    r.create_object(instance_path('shared'), True)
    r.share_folder(instance_path('shared'))
    shutil.move(instance_path('foo'), instance_path('shared', 'foo'))


def observer_linux():
    observe_folders_virtual()
    wait_for_any(InstanceUniqueDirTree({
        'shared': {
            'foo': {
                'bar': {
                    'baz': 'qux',
                    'BAZ': 'quux'
                },
                'BAR': {}
            },
            '.aerofs': ''
        }
    }, ignore_content=['.aerofs']))


def observer_win():
    _observer_case_insensitive('desktop.ini')


def observer_osx():
    _observer_case_insensitive('Icon\r')


def _observer_case_insensitive(junk):
    observe_folders_virtual()
    # you'd think migration would preserve what is visible
    # unfortunately race conditions exist that allow conflicting files to appear first
    wait_for_any(
        InstanceUniqueDirTree({
            'shared': {
                'foo': {
                    'bar': {
                        'baz': 'qux'
                    }
                },
                '.aerofs': '',
                junk: ''
            }
        }, ignore_content=['.aerofs', junk]),
        InstanceUniqueDirTree({
            'shared': {
                'foo': {
                    'bar': {
                        'BAZ': 'quux'
                    }
                },
                '.aerofs': '',
                junk: ''
            }
        }, ignore_content=['.aerofs', junk]),
        InstanceUniqueDirTree({
            'shared': {
                'foo': {
                    'BAR': {}
                },
                '.aerofs': '',
                junk: ''
            }
        }, ignore_content=['.aerofs', junk]))

    nros = ritual.connect().list_non_representable_objects()
    assertFalse(instance_path('foo', 'BAR') in nros)
    assertFalse(instance_path('foo', 'bar', 'BAZ') in nros)
    assertTrue(instance_path('shared', 'foo', 'BAR') in nros
               or instance_path('shared', 'foo', 'bar') in nros)
    assertTrue(instance_path('shared', 'foo', 'bar', 'BAZ') in nros
               or instance_path('shared', 'foo', 'bar', 'baz') in nros)


spec = mkspec(creator, {
    'linux': observer_linux,
    'win': observer_win,
    'osx': observer_osx
})
