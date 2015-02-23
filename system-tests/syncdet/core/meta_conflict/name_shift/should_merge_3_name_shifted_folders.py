
from .common import NameShiftTester

_DIR_NAMES = ['dir{0}'.format(i) for i in range(1,4)]
_CONFLICT_NAMES = ['conflict{0}'.format(i) for i in range(1,4)]
_FILE_NAMES = ['f{0}'.format(i) for i in range(1,4)]
_FILE_CONTENTS = ["pick a little",
                  "talk a little",
                  "cheep cheep cheep talk a lot pick a little more"]

name_shift_tester = NameShiftTester(_FILE_NAMES, _FILE_CONTENTS, _DIR_NAMES, _CONFLICT_NAMES)

# This test is only defined to resolve name conflicts between two actors. All
# others wait until resolution is complete, then download the files
spec = { 'entries': [lambda: name_shift_tester.creator(),
                     lambda: name_shift_tester.shifter()],
         'default': lambda: name_shift_tester.receiver() }
