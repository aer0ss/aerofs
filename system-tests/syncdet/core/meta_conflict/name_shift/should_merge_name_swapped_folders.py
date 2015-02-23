"""
This test generates a name conflict on two folders, say n1 and n2. We say
swapped because the object identifiers for n1 and n2 are swapped on the two
devices in the test.  That is, we want to get the two devices into the
following state before performing aliasing

  A          B
n1o1       n2o1
n2o2       n1o2

The reader may note that there appears to be a deadlock of OID/name
dependencies: it is the point of this test to create such a deadlock.

The success of the test is verified by ensuring that two subfiles are
contained in each of the folders at the end of the test---at the initial
state, there is only one file per folder on each device.
"""

from .common import NameShiftTester

_DIR_NAMES = ['dir1', 'dir2']
_CONFLICT_NAMES = ['conflict1', 'conflict2']
_FILE_NAMES = ['file1', 'file2']
_FILE_CONTENTS = ['Youve got to pick a pocket or two',
                  'Seventy-six trombones']

name_shift_tester = NameShiftTester(_FILE_NAMES, _FILE_CONTENTS,
    _DIR_NAMES, _CONFLICT_NAMES)

# This test is only defined to resolve name conflicts between two actors. All
# others wait until resolution is complete, then download the files
spec = { 'entries': [lambda: name_shift_tester.creator(),
                     lambda: name_shift_tester.shifter()],
         'default': lambda: name_shift_tester.receiver() }
