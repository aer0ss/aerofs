"""
Check that filenames with forbidden characters and reserved filenames sync on the VFS
and that they appear on the physical FS as expected on each OS

dirtree of the form:

    {
        "forbidden": {"sub": "forbidden"},
        ...
    }

"""

from . import mkspec, folders_creator, observe_folders_virtual, observe_folders_physical, UNSYNCABLE_NAMES


def observer_unrestricted():
    observe_folders_virtual(UNSYNCABLE_NAMES)
    observe_folders_physical(UNSYNCABLE_NAMES)


def observer_win():
    observe_folders_virtual(UNSYNCABLE_NAMES)
    # well, lookit dat, these are merely "problematic" and "not recommended" but
    # can actually exist on the filesystem, (technically, all the others can
    # also exist on the filesystem but for some reason Java still chokes on them.
    # Oh well...
    observe_folders_physical([
        'foo ',
        'foo.',
        'NUL',
        'com2.txt'
    ])


spec = mkspec(folders_creator(UNSYNCABLE_NAMES), {
    'linux': observer_unrestricted,
    'win': observer_win,
    'osx': observer_unrestricted
})
