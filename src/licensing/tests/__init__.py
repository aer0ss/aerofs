import unittest

def test_suite():
    import tests.test_license_file

    suite = unittest.TestSuite()

    # Add all tests
    suite.addTest(tests.test_license_file.test_suite())

    return suite
