import unittest

def test_suite():
    # import all unit test files here
    import unittests.aerofs.test_configuration
    import unittests.aerofs.test_exception
    import unittests.aerofs.test_id

    suite = unittest.TestSuite()

    # Add all test suites here
    suite.addTest(unittests.aerofs.test_configuration.test_suite())
    suite.addTest(unittests.aerofs.test_exception.test_suite())
    suite.addTest(unittests.aerofs.test_id.test_suite())

    return suite
