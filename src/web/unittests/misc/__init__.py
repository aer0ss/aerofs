import unittest

def test_suite():
    # import all test submodules
    import unittests.misc.error_test
    import unittests.misc.exception_reply_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.misc.error_test.test_suite())
    suite.addTest(unittests.misc.exception_reply_test.test_suite())

    return suite
