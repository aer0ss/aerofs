import unittest

def test_suite():
    # import all test submodules
    import apps_view_test, license_test, monitoring_test, mdm_test

    suite = unittest.TestSuite()

    # add all unit tests
    suite.addTest(apps_view_test.test_suite())
    suite.addTest(monitoring_test.test_suite())
    suite.addTest(license_test.test_suite())
    suite.addTest(mdm_test.test_suite())

    return suite
