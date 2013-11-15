import unittest


def test_suite():
    # import all test submodules
    import password_reset_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(password_reset_test.test_suite())

    return suite
