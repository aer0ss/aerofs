from false_conflict_test import FalseConflictTest

test = FalseConflictTest("0123" * 1024 * 1024)

spec = { 'entries': [ test.file_seeder ], 'default': test.file_modifier }
