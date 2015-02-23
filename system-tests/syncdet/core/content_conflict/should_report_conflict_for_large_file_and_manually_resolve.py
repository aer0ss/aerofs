from manual_conflict_resolution_test import ManualConflictResolutionTest

test = ManualConflictResolutionTest("0123" * 1024 * 1024)

spec = {
    'entries': [test.resolver],
    'default': test.spectator,
    'timeout': 15 * 60
}
