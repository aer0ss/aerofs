"""
Check that filenames with unicode characters sync on the VFS, regardless of normalization
and that they appear on the physical FS as expected on each OS
"""

from . import mkspec, folders_creator, observe_folders_virtual, observe_folders_physical


NFC_NFD_FILENAMES = [
    u'\xe9',    # lowercase eacute, NFC
    u'e\u0301'  # lowercase eacute, NFD
]


def observer_normalization_sensitive():
    observe_folders_virtual(NFC_NFD_FILENAMES)
    observe_folders_physical(NFC_NFD_FILENAMES)


def observer_normalizer_NFD():
    observe_folders_virtual(NFC_NFD_FILENAMES)
    observe_folders_physical([u'e\u0301'])


spec = mkspec(folders_creator(NFC_NFD_FILENAMES), {
    'linux': observer_normalization_sensitive,
    'win': observer_normalization_sensitive,
    'osx': observer_normalizer_NFD
})
