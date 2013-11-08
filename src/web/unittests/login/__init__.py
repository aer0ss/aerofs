import unittest

def test_suite():
    # import all test submodules
    import unittests.login.login_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.login.login_test.test_suite())

    return suite

