import unittest

def test_suite():
    # import all test submodules
    import unittests.accept.accept_view_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.accept.accept_view_test.test_suite())

    return suite
