"""
Common methods for tests related to non-representable objects aka "unsyncable" files
"""

import os
import sys
import functools
from lib.files import instance_unique_path, wait_dir, instance_path
from syncdet.case import actor_id, actor_count, instance_unique_hash32
from syncdet.case.sync import sync
from lib.files.dirtree import InstanceUniqueDirTree, wait_for_any
from lib import ritual


UNSYNCABLE_NAMES = [
    'f<o>o',
    'foo:bar',
    'foo|bar',
    'foo?bar',
    'foo*bar',
    'foo"bar',
    'foo ',
    'foo.',
    'NUL',
    'com2.txt'
]


CASE_CONFLICT_NAMES = [
    "foo",
    "Foo",
    "fOo",
    "FOo",
    "foO",
    "FoO",
    "fOO",
    "FOO",
]

is_creator = False


def _is_lucky():
    luck = instance_unique_hash32() % actor_count()
    return luck == actor_id()


def _main(creator, observers):
    if _is_lucky():
        global is_creator
        is_creator = True
        #
        # IMPORTANT: for better repeatability and easier specification of expected state
        # the creator uses a sync() primitive to ensure a consistent global order of
        # propagation of the initial objects
        #
        creator()

    if 'linux' in sys.platform:
        observers['linux']()
    elif 'cygwin' in sys.platform or 'win32' in sys.platform:
        observers['win']()
    elif 'darwin' in sys.platform:
        observers['osx']()
    else:
        raise ValueError("Unsupported OS: {}".format(sys.platform))


def _file_creator(names):
    os.makedirs(instance_unique_path())

    r = ritual.connect()
    r.wait_path(instance_unique_path())
    n = 0
    for name in names:
        r.write_file(instance_path(name), name)
        sync('VFS {}'.format(n))
        n += 1


def files_creator(names):
    return functools.partial(_file_creator, names)


def observe_files_virtual(names):
    wait_dir(instance_unique_path())
    r = ritual.connect()
    n = 0
    for name in names:
        r.wait_file_with_content(instance_path(name), name)
        if not is_creator:
            sync('VFS {}'.format(n))
        n += 1


def _dt_files(*names):
    return InstanceUniqueDirTree(dict([(name, name) for name in names]))


def observe_files_physical(names):
    wait_for_any(_dt_files(*names))


def observe_any_files_physical(names):
    wait_for_any(*[_dt_files(name) for name in names])


def _folders_creator(names):
    os.makedirs(instance_unique_path())

    r = ritual.connect()
    r.wait_path(instance_unique_path())
    n = 0
    for name in names:
        r.create_object(instance_path(name), True)
        r.write_file(os.path.join(instance_path(name), 'sub'), name)
        sync('VFS {}'.format(n))
        n += 1


def folders_creator(names):
    return functools.partial(_folders_creator, names)


def observe_folders_virtual(names):
    wait_dir(instance_unique_path())
    r = ritual.connect()
    n = 0
    for name in names:
        r.wait_file_with_content(os.path.join(instance_path(name), 'sub'), name)
        if not is_creator:
            sync('VFS {}'.format(n))
        n += 1


def _dt_folders(*names):
    return InstanceUniqueDirTree(dict([(name, {'sub': name}) for name in names]))


def observe_folders_physical(names):
    wait_for_any(_dt_folders(*names))


def observe_any_folders_physical(names):
    wait_for_any(*[_dt_folders(name) for name in names])


def mkspec(creator, observers):
    """
    @param creator: OS-agnostic test initialization (should use Ritual instead of direct FS interaction)
    @param observers: platform-specific observers of final state
    @return: system test spec
    """
    return {'default': functools.partial(_main, creator, observers)}
