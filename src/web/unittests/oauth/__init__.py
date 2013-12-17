import unittest

def test_suite():
    # import all test submodules
    import unittests.oauth.authorize_view_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.oauth.authorize_view_test.test_suite())

    return suite
