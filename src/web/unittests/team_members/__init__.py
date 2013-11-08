import unittest

def test_suite():
    # import all test submodules
    import unittests.team_members.team_members_view_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.team_members.team_members_view_test.test_suite())

    return suite
