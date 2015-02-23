"""
Check that filenames with unicode characters sync on the VFS, regardless of normalization
and that they appear on the physical FS as expected on each OS
"""

from . import mkspec, files_creator, observe_files_virtual, observe_files_physical


NFC_NFD_FILENAMES = [
    u'\xe9',    # lowercase eacute, NFC
    u'e\u0301'  # lowercase eacute, NFD
]


def observer_normalization_insensitive():
    observe_files_virtual(NFC_NFD_FILENAMES)
    observe_files_physical(NFC_NFD_FILENAMES)


def observer_normalizer_NFD():
    observe_files_virtual(NFC_NFD_FILENAMES)
    observe_files_physical([u'e\u0301'])


spec = mkspec(files_creator(NFC_NFD_FILENAMES), {
    'linux': observer_normalization_insensitive,
    'win': observer_normalization_insensitive,
    'osx': observer_normalizer_NFD
})
