import unittest

def test_suite():
    # import all test submodules
    import unittests.devices.timestamp_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.devices.timestamp_test.test_suite())

    return suite