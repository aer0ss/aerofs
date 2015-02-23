from false_conflict_test import FalseConflictTest

test = FalseConflictTest("The Walking Dead")

spec = { 'entries': [ test.file_seeder ], 'default': test.file_modifier }