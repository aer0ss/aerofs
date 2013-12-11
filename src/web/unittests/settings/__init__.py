import unittest

def test_suite():
    # import all test submodules
    import settings_view_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(settings_view_test.test_suite())

    return suite
