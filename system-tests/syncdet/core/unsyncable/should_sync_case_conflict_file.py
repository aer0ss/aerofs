"""
Check that filenames with conflicting case sync on the VFS
and that they appear on the physical FS as expected on each OS
"""

from . import mkspec, files_creator, observe_files_virtual, observe_files_physical, observe_any_files_physical, CASE_CONFLICT_NAMES


def observer_case_sensitive():
    observe_files_virtual(CASE_CONFLICT_NAMES)
    observe_files_physical(CASE_CONFLICT_NAMES)


def observer_case_insensitive():
    observe_files_virtual(CASE_CONFLICT_NAMES)
    observe_any_files_physical(CASE_CONFLICT_NAMES)


spec = mkspec(files_creator(CASE_CONFLICT_NAMES), {
    'linux': observer_case_sensitive,
    'win': observer_case_insensitive,
    'osx': observer_case_insensitive
})
