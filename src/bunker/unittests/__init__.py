import unittest


def test_suite():
    # import all test modules
    import unittests.maintenance
    import unittests.setup

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.maintenance.test_suite())
    suite.addTest(unittests.setup.test_suite())

    return suite
