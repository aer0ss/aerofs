import unittest

def test_suite():
    # import all test submodules
    import unittests.payment.stripe_util_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(unittests.payment.stripe_util_test.test_suite())

    return suite
