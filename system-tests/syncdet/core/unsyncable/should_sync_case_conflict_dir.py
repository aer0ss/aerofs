"""
Check that filenames with conflicting case sync on the VFS
and that they appear on the physical FS as expected on each OS
"""

from . import mkspec, folders_creator, observe_folders_virtual, observe_folders_physical, observe_any_folders_physical, CASE_CONFLICT_NAMES


def observer_case_sensitive():
    observe_folders_virtual(CASE_CONFLICT_NAMES)
    observe_folders_physical(CASE_CONFLICT_NAMES)


def observer_case_insensitive():
    observe_folders_virtual(CASE_CONFLICT_NAMES)
    observe_any_folders_physical(CASE_CONFLICT_NAMES)


spec = mkspec(folders_creator(CASE_CONFLICT_NAMES), {
    'linux': observer_case_sensitive,
    'win': observer_case_insensitive,
    'osx': observer_case_insensitive
})
