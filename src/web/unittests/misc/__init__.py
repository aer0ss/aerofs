import unittest

def test_suite():
    # import all test submodules
    import error_test
    import version_test
    import exception_reply_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(error_test.test_suite())
    suite.addTest(version_test.test_suite())
    suite.addTest(exception_reply_test.test_suite())

    return suite
