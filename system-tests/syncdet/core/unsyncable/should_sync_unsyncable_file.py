"""
Check that filenames with forbidden characters and reserved filenames sync on the VFS
and that they appear on the physical FS as expected on each OS

dirtree of the form:

    {
        "forbidden": "forbidden",
        ...
    }

"""

from . import mkspec, files_creator, observe_files_virtual, observe_files_physical, UNSYNCABLE_NAMES


def observer_unrestricted():
    observe_files_virtual(UNSYNCABLE_NAMES)
    observe_files_physical(UNSYNCABLE_NAMES)


def observer_win():
    observe_files_virtual(UNSYNCABLE_NAMES)
    # well, lookit dat, these are merely "problematic" and "not recommended" but
    # can actually exist on the filesystem, (technically, all the others can
    # also exist on the filesystem but for some reason Java still chokes on them.
    # Oh well...
    observe_files_physical([
        'foo ',
        'foo.',
        'NUL',
        'com2.txt'
    ])


spec = mkspec(files_creator(UNSYNCABLE_NAMES), {
    'linux': observer_unrestricted,
    'win': observer_win,
    'osx': observer_unrestricted
})
