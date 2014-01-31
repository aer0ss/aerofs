import unittest

def test_suite():
    import tests.test_license_file
    import tests.test_unicode_csv

    suite = unittest.TestSuite()

    # Add all tests
    suite.addTest(tests.test_license_file.test_suite())
    suite.addTest(tests.test_unicode_csv.test_suite())

    return suite
