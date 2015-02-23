from manual_conflict_resolution_test import ManualConflictResolutionTest

test = ManualConflictResolutionTest("Breaking Bad")

spec = { 'entries': [ test.resolver ], 'default': test.spectator }
