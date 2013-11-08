import unittest

def test_suite():
    # import all test submodules
    import unittests.setup.setup_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.setup.setup_test.test_suite())

    return suite
