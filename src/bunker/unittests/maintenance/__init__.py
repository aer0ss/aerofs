import unittest

def test_suite():
    # import all test submodules
    import apps_view_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(apps_view_test.test_suite())

    return suite
