import unittest


def test_suite():
    # import all test modules
    import unittests.maintenance

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.maintenance.test_suite())

    return suite
